package com.lipabill.app.data.tickets.smart

import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Checks a draft before the person sees it: marks values that look wrong and
 * compares the passenger with the name they keep on their tickets.
 */
object PassValidator {

    private val AIRPORT = Regex("[A-Z]{3}")
    private val FLIGHT = Regex("[A-Z0-9]{2}\\s?\\d{1,4}[A-Z]?")
    private val TITLES = setOf("mr", "mrs", "ms", "miss", "mstr", "dr", "prof")

    fun validate(draft: PassDraft, today: LocalDate, holderName: String?): PassDraft {
        val checked = draft.fields.mapValues { (field, value) ->
            val problem = problemWith(field, value.value, draft.kind, today)
            when {
                problem != null -> value.copy(warning = problem)
                value.source == FieldSource.AI && value.warning == null ->
                    value.copy(warning = "Read by on-device AI. Check it.")
                else -> value
            }
        }
        val blockers = buildList {
            if (draft.barcodeValue.isNullOrBlank()) add("No scannable code was found. Scan the ticket's barcode or QR.")
        }
        return draft.copy(
            fields = checked,
            blockers = blockers,
            nameCheck = nameCheck(draft[PassField.HOLDER], holderName)
        )
    }

    internal fun problemWith(field: PassField, value: String, kind: PassKind, today: LocalDate): String? =
        when (field) {
            PassField.ORIGIN, PassField.DESTINATION ->
                if (kind == PassKind.AIRLINE && !AIRPORT.matches(value)) "Airport codes are three letters, like NBO." else null
            PassField.FLIGHT ->
                if (!FLIGHT.matches(value.uppercase(Locale.US))) "Flight numbers look like KQ 100." else null
            PassField.DATE -> {
                val date = parseDraftDate(value)
                when {
                    date == null -> "Use the date as YYYY-MM-DD."
                    date.isBefore(today.minusDays(1)) -> "This date has passed."
                    ChronoUnit.DAYS.between(today, date) > 400 -> "This date is more than a year away."
                    else -> null
                }
            }
            PassField.TIME -> if (parseDraftTime(value) == null) "Use the time as HH:MM." else null
            else -> null
        }

    /** Same person whatever the order, case, accents or titles: "KAMAU/JOHN MR" matches "John Kamau". */
    fun nameCheck(holder: String?, profileName: String?): NameCheck {
        val a = nameTokens(holder)
        val b = nameTokens(profileName)
        if (a.isEmpty() || b.isEmpty()) return NameCheck.UNKNOWN
        val shared = a.intersect(b)
        val smaller = minOf(a.size, b.size)
        return if (shared.size >= minOf(2, smaller)) NameCheck.MATCH else NameCheck.MISMATCH
    }

    internal fun nameTokens(name: String?): Set<String> {
        if (name.isNullOrBlank()) return emptySet()
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase(Locale.US)
            .split(Regex("[^a-z]+"))
            .filter { it.length > 1 && it !in TITLES }
            .toSet()
    }
}

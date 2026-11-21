package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.tickets.EventTicketExtractor
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.TicketDocumentKind
import java.util.Locale

/**
 * Rules-first document type. Never decides on the word "ticket" alone, and treats the text
 * strictly as data. A kind the user picked when adding wins unless the text clearly disagrees.
 */
object DocumentClassifier {

    private val RAIL_WORDS = listOf(
        "sgr", "madaraka", "kenya railways", "coach", "terminus", "train", "inter county", "economy class",
        // Swahili
        "abiria", "behewa", "kituo"
    )
    private val EVENT_WORDS = listOf(
        "venue", "admission", "admit", "gate", "entry", "doors", "vip", "regular", "early bird",
        "section", "row", "organiser", "organizer", "event", "festival", "concert", "match",
        "stadium", "kick-off", "kick off", "attendee", "tier",
        // Swahili
        "tiketi", "kiingilio", "uwanja", "kiti"
    )

    /** Soften common OCR swaps (0/O, 1/l/I) before keyword matching. */
    internal fun normalizeForClassify(text: String): String {
        // Only normalize within likely keyword tokens — keep booking refs intact by working on a copy for matching.
        return text.lowercase(Locale.US)
            .replace(Regex("""(?<=[a-z])0(?=[a-z])"""), "o")
            .replace(Regex("""(?<=[a-z])1(?=[a-z])"""), "l")
            .replace('|', 'l')
    }

    fun classify(
        text: String,
        hasBarcode: Boolean,
        hint: TicketDocumentKind = TicketDocumentKind.AUTO
    ): ClassificationResult {
        val lower = normalizeForClassify(text)
        if (hint == TicketDocumentKind.PKPASS) {
            return ClassificationResult(PassKind.EVENT, 0.95f, listOf("wallet pass"), false)
        }
        if (EventTicketExtractor.looksLikeFlight(text)) {
            return ClassificationResult(PassKind.OTHER, 0.9f, listOf("airline itinerary"), true)
        }
        val rail = RAIL_WORDS.count { lower.contains(it) } +
            if (EventTicketSms.looksLikeRailBooking(text)) 2 else 0
        val event = EVENT_WORDS.count { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
        val evidence = buildList {
            if (rail > 0) add("rail keywords ($rail)")
            if (event > 0) add("event keywords ($event)")
            if (hasBarcode) add("scannable code")
        }
        val (kind, score) = when {
            rail >= 2 && rail >= event -> PassKind.RAIL to scoreFor(rail)
            event >= 2 -> PassKind.EVENT to scoreFor(event)
            else -> PassKind.OTHER to 0.3f
        }
        val codeBonus = if (hasBarcode && kind != PassKind.OTHER) 0.05f else 0f
        var result = ClassificationResult(
            kind = kind,
            confidence = (score + codeBonus).coerceAtMost(0.97f),
            evidence = evidence,
            needsUserConfirmation = score + codeBonus < ConfidencePolicy.CLASSIFY_CONFIRM_BELOW
        )
        // The user's choice when adding counts as strong evidence.
        val picked = when (hint) {
            TicketDocumentKind.EVENT -> PassKind.EVENT
            TicketDocumentKind.SGR_TICKET -> PassKind.RAIL
            else -> null
        }
        if (picked != null) {
            val disagrees = result.kind != PassKind.OTHER && result.kind != picked &&
                result.confidence >= 0.8f
            result = if (disagrees) {
                result.copy(evidence = result.evidence + "chosen when adding", needsUserConfirmation = true)
            } else {
                ClassificationResult(
                    kind = picked,
                    confidence = maxOf(0.85f, if (result.kind == picked) result.confidence else 0f),
                    evidence = result.evidence + "chosen when adding",
                    needsUserConfirmation = false
                )
            }
        }
        return result
    }

    /** Two signals 0.70, each more +0.06, capped below certainty. */
    private fun scoreFor(hits: Int): Float = (0.58f + hits * 0.06f).coerceAtMost(0.92f)
}

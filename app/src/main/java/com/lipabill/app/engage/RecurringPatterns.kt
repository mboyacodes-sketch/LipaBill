package com.lipabill.app.engage

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.viewmodel.Cadence
import com.lipabill.app.viewmodel.PriceTrend
import com.lipabill.app.viewmodel.RegularChange
import com.lipabill.app.viewmodel.RegularPayee
import com.lipabill.app.viewmodel.detectRegulars
import com.lipabill.app.viewmodel.payeeMarkCandidates
import java.time.LocalDate
import java.time.ZoneId

/** User confirm / dismiss for a detected recurring payee. Pending = not answered yet. */
enum class RecurringChoice {
    Pending,
    Confirmed,
    Dismissed
}

/**
 * A bill-like rhythm found by reading the ledger only.
 * Never written back as a transaction row — only the user's [choice] is stored in prefs.
 */
data class RecurringPattern(
    val key: String,
    val name: String,
    val cadence: Cadence,
    val typicalAmount: Double,
    val lastPaid: LocalDate,
    val nextDue: LocalDate,
    val payments: Int,
    val choice: RecurringChoice = RecurringChoice.Pending
)

object RecurringPatterns {

    const val POTENTIAL_CAP = 5

    /** Encode for SecurePreferences: `payeeKey=confirmed` / `payeeKey=dismissed`. */
    fun encodeChoice(key: String, choice: RecurringChoice): String? {
        if (key.isBlank()) return null
        return when (choice) {
            RecurringChoice.Pending -> null
            RecurringChoice.Confirmed -> "$key=confirmed"
            RecurringChoice.Dismissed -> "$key=dismissed"
        }
    }

    fun parseChoices(encoded: Set<String>): Map<String, RecurringChoice> {
        val out = linkedMapOf<String, RecurringChoice>()
        for (raw in encoded) {
            val eq = raw.indexOf('=')
            if (eq <= 0) continue
            val key = raw.substring(0, eq)
            val value = raw.substring(eq + 1)
            val choice = when (value) {
                "confirmed" -> RecurringChoice.Confirmed
                "dismissed" -> RecurringChoice.Dismissed
                else -> continue
            }
            out[key] = choice
        }
        return out
    }

    fun serializeChoices(choices: Map<String, RecurringChoice>): Set<String> =
        choices.mapNotNull { (key, choice) -> encodeChoice(key, choice) }.toSet()

    /**
     * Cadence detector over ledger rows. [choices] from prefs; dismissed stay out of
     * [potential] and out of ranked regulars / Coming up; confirmed pin to the top
     * and feed next-bill.
     */
    fun detect(
        txs: Iterable<MpesaTransaction>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        choices: Map<String, RecurringChoice> = emptyMap(),
        essentials: Set<String> = emptySet(),
        watched: Set<String> = emptySet()
    ): List<RecurringPattern> {
        val confirmedKeys = choices.filterValues { it == RecurringChoice.Confirmed }.keys
        return patternsFromRegulars(
            detectRegulars(
                txs = txs,
                zone = zone,
                today = today,
                essentials = essentials,
                watched = watched + confirmedKeys
            ),
            choices
        )
    }

    /** Map already-detected regulars — avoids a second [detectRegulars] pass on Insights. */
    fun patternsFromRegulars(
        regulars: List<RegularPayee>,
        choices: Map<String, RecurringChoice>
    ): List<RecurringPattern> =
        regulars.map { regular ->
            RecurringPattern(
                key = regular.key,
                name = regular.name,
                cadence = regular.cadence,
                typicalAmount = regular.typicalAmount,
                lastPaid = regular.lastPaid,
                nextDue = regular.nextDue,
                payments = regular.payments,
                choice = choiceFor(regular.key, regular.name, choices)
            )
        }

    /**
     * Apply Watching-what-repeats answers to detector output:
     * - Dismissed → drop (Regulars, Coming up, safe-to-spend bills)
     * - Confirmed → mark watched and rank above other regulars
     * - Pending → keep detector order among non-confirmed
     *
     * Call whenever ledger or [choices] change so Insights/Habits re-rank together.
     */
    fun applyChoices(
        regulars: List<RegularPayee>,
        choices: Map<String, RecurringChoice>
    ): List<RegularPayee> {
        if (regulars.isEmpty()) return regulars
        if (choices.isEmpty()) return regulars.sortedByDescending { it.monthlyCost }
        val kept = regulars.mapNotNull { regular ->
            when (choiceFor(regular.key, regular.name, choices)) {
                RecurringChoice.Dismissed -> null
                RecurringChoice.Confirmed -> regular.copy(watched = true)
                RecurringChoice.Pending -> regular
            }
        }
        return kept.sortedWith(
            compareByDescending<RegularPayee> {
                choiceFor(it.key, it.name, choices) == RecurringChoice.Confirmed
            }
                .thenByDescending { it.watched }
                .thenByDescending { it.essential }
                .thenByDescending { it.monthlyCost }
        )
    }

    /** True when prefs say this payee is not a bill (name or phone key). */
    fun isDismissed(
        key: String?,
        name: String,
        choices: Map<String, RecurringChoice>
    ): Boolean {
        if (choices.isEmpty()) return false
        val resolved = key?.takeIf { it.isNotBlank() } ?: HabitPatterns.payeeKey(name)
        return choiceFor(resolved, name, choices) == RecurringChoice.Dismissed
    }

    /** Drop Watching rows for payees the user dismissed as not a bill. */
    fun filterChanges(
        changes: List<RegularChange>,
        choices: Map<String, RecurringChoice>
    ): List<RegularChange> {
        if (choices.isEmpty() || changes.isEmpty()) return changes
        return changes.filterNot { change -> isDismissed(null, change.name, choices) }
    }

    /** Drop price trends for dismissed payees. */
    fun filterTrends(
        trends: List<PriceTrend>,
        choices: Map<String, RecurringChoice>
    ): List<PriceTrend> {
        if (choices.isEmpty() || trends.isEmpty()) return trends
        return trends.filterNot { trend -> isDismissed(null, trend.name, choices) }
    }

    /**
     * Prefs may still hold a legacy name key after identity moved to phone-first.
     * Match any [payeeMarkCandidates] alias so confirm/dismiss still stick.
     */
    fun choiceFor(
        key: String,
        name: String,
        choices: Map<String, RecurringChoice>
    ): RecurringChoice {
        choices[key]?.let { return it }
        for (alias in payeeMarkCandidates(name, null, key)) {
            choices[alias]?.let { return it }
        }
        return RecurringChoice.Pending
    }

    /** Pending only — Insights “Potential recurring payment” list. */
    fun potential(patterns: List<RecurringPattern>, limit: Int = POTENTIAL_CAP): List<RecurringPattern> =
        patterns.filter { it.choice == RecurringChoice.Pending }.take(limit)

    fun confirmed(patterns: List<RecurringPattern>): List<RecurringPattern> =
        patterns.filter { it.choice == RecurringChoice.Confirmed }

    /**
     * Short catch-up / Habits label: soonest confirmed nextDue, else soonest pending.
     * Dismissed never appear.
     */
    fun nextBillLabel(
        patterns: List<RecurringPattern>,
        today: LocalDate = LocalDate.now()
    ): String? {
        val pool = confirmed(patterns).ifEmpty {
            patterns.filter { it.choice == RecurringChoice.Pending }
        }
        if (pool.isEmpty()) return null
        val next = pool.minWith(
            compareBy<RecurringPattern> { it.nextDue }
                .thenByDescending { it.typicalAmount }
        )
        return billShortLabel(next.name)
    }

    fun billShortLabel(name: String): String {
        val key = HabitPatterns.payeeKey(name)
        return when {
            "kplc" in key || "kenya power" in key -> "KPLC"
            "water" in key || "sewerage" in key -> "Water"
            else -> name.split(" ").firstOrNull()?.take(12) ?: name.take(12)
        }
    }

    /**
     * Habits catch-up path: map [ChallengeTransaction] rows into the same detector
     * without touching TransactionDao writes.
     */
    fun detectFromChallenge(
        rows: List<ChallengeTransaction>,
        today: LocalDate,
        zone: ZoneId,
        choices: Map<String, RecurringChoice> = emptyMap(),
        essentials: Set<String> = emptySet()
    ): List<RecurringPattern> {
        val txs = rows.mapIndexed { index, row ->
            MpesaTransaction(
                id = row.id,
                code = "C${row.id.coerceAtLeast(0)}-$index",
                type = row.type,
                amount = row.amountCents / 100.0,
                counterpartyName = row.counterpartyName,
                counterpartyPhone = row.counterpartyPhone,
                timestampMillis = row.timestampMillis,
                balance = null,
                cost = row.feeCents / 100.0,
                rawBody = ""
            )
        }
        return detect(txs, today, zone, choices, essentials)
    }

    /** Drop NewCharge rows already shown as potential recurring. */
    fun suppressNewChargesAlreadyPotential(
        changes: List<com.lipabill.app.viewmodel.RegularChange>,
        potential: List<RecurringPattern>
    ): List<com.lipabill.app.viewmodel.RegularChange> {
        if (potential.isEmpty()) return changes
        val names = potential.mapTo(HashSet()) { it.name }
        return changes.filterNot { change ->
            change is com.lipabill.app.viewmodel.RegularChange.NewCharge && change.name in names
        }
    }
}

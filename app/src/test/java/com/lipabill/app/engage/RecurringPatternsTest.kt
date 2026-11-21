package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import com.lipabill.app.viewmodel.Cadence
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPatternsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-18")
    private var n = 0

    private fun pay(date: LocalDate, amount: Double, name: String) =
        testTransaction(
            code = "R${n++}",
            type = TransactionType.PAYBILL,
            amount = amount,
            counterpartyName = name,
            timestampMillis = date.atTime(10, 0).toInstant(zone).toEpochMilli()
        )

    private fun monthly(name: String, amounts: List<Double>, lastDate: LocalDate) =
        amounts.reversed().mapIndexed { i, amount ->
            pay(lastDate.minusMonths(i.toLong()), amount, name)
        }

    @Test
    fun detect_marks_steady_bill_as_pending() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2000.0), LocalDate.parse("2026-11-10"))
        val patterns = RecurringPatterns.detect(dstv, today, zone)
        assertEquals(1, patterns.size)
        assertEquals(RecurringChoice.Pending, patterns.single().choice)
        assertEquals(Cadence.MONTHLY, patterns.single().cadence)
        assertEquals(1, RecurringPatterns.potential(patterns).size)
    }

    @Test
    fun dismissed_is_not_in_potential() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2000.0), LocalDate.parse("2026-11-10"))
        val key = RecurringPatterns.detect(dstv, today, zone).single().key
        val patterns = RecurringPatterns.detect(
            dstv,
            today,
            zone,
            choices = mapOf(key to RecurringChoice.Dismissed)
        )
        assertTrue(RecurringPatterns.potential(patterns).isEmpty())
        assertEquals(RecurringChoice.Dismissed, patterns.single().choice)
    }

    @Test
    fun confirmed_feeds_next_bill_before_pending() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2000.0), LocalDate.parse("2026-11-10"))
        val kplc = monthly("KPLC Prepaid", listOf(800.0, 900.0, 850.0, 880.0), LocalDate.parse("2026-11-08"))
        val all = dstv + kplc
        val base = RecurringPatterns.detect(all, today, zone)
        val kplcKey = base.first { "kplc" in it.key || it.name.contains("KPLC", ignoreCase = true) }.key
        val patterns = RecurringPatterns.detect(
            all,
            today,
            zone,
            choices = mapOf(kplcKey to RecurringChoice.Confirmed)
        )
        assertEquals("KPLC", RecurringPatterns.nextBillLabel(patterns, today))
        assertEquals(1, RecurringPatterns.confirmed(patterns).size)
    }

    @Test
    fun next_bill_falls_back_to_pending_when_none_confirmed() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2000.0), LocalDate.parse("2026-11-10"))
        val patterns = RecurringPatterns.detect(dstv, today, zone)
        assertEquals("DSTV", RecurringPatterns.nextBillLabel(patterns, today))
    }

    @Test
    fun encode_parse_round_trip() {
        val encoded = RecurringPatterns.serializeChoices(
            mapOf(
                "name:dstv kenya" to RecurringChoice.Confirmed,
                "name:netflix" to RecurringChoice.Dismissed,
                "name:ignored" to RecurringChoice.Pending
            )
        )
        assertEquals(
            setOf("name:dstv kenya=confirmed", "name:netflix=dismissed"),
            encoded
        )
        val parsed = RecurringPatterns.parseChoices(encoded)
        assertEquals(RecurringChoice.Confirmed, parsed["name:dstv kenya"])
        assertEquals(RecurringChoice.Dismissed, parsed["name:netflix"])
        assertNull(parsed["name:ignored"])
    }

    @Test
    fun suppress_new_charge_when_already_potential() {
        val potential = listOf(
            RecurringPattern(
                key = "name:zoom gym",
                name = "ZOOM GYM",
                cadence = Cadence.MONTHLY,
                typicalAmount = 3000.0,
                lastPaid = today,
                nextDue = today.plusDays(30),
                payments = 3,
                choice = RecurringChoice.Pending
            )
        )
        val changes = listOf(
            com.lipabill.app.viewmodel.RegularChange.NewCharge("ZOOM GYM", 3000.0, Cadence.MONTHLY),
            com.lipabill.app.viewmodel.RegularChange.Stopped("NETFLIX", today.minusMonths(2), 1100.0)
        )
        val filtered = RecurringPatterns.suppressNewChargesAlreadyPotential(changes, potential)
        assertEquals(1, filtered.size)
        assertTrue(filtered.single() is com.lipabill.app.viewmodel.RegularChange.Stopped)
    }

    @Test
    fun legacy_name_choice_still_matches_phone_keyed_regular() {
        assertEquals(
            RecurringChoice.Confirmed,
            RecurringPatterns.choiceFor(
                key = "tel:700111222",
                name = "Landlord",
                choices = mapOf("landlord" to RecurringChoice.Confirmed)
            )
        )
        assertEquals(
            RecurringChoice.Pending,
            RecurringPatterns.choiceFor("tel:700111222", "Landlord", emptyMap())
        )
    }

    @Test
    fun challenge_rows_detect_kplc_label() {
        val day = 24L * 60 * 60 * 1000
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        val rows = listOf(
            ChallengeTransaction(1, now - 60 * day, "KPLC Prepaid", TransactionType.PAYBILL, 80_000),
            ChallengeTransaction(2, now - 30 * day, "KPLC Prepaid", TransactionType.PAYBILL, 90_000),
            ChallengeTransaction(3, now - 2 * day, "KPLC Prepaid", TransactionType.PAYBILL, 85_000)
        )
        val patterns = RecurringPatterns.detectFromChallenge(rows, today, zone)
        assertTrue(patterns.isNotEmpty())
        assertEquals("KPLC", RecurringPatterns.nextBillLabel(patterns, today))
    }
}

package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.parser.MpesaSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxScanTest {

    @Test
    fun first_scan_reads_the_whole_inbox() {
        assertNull(InboxScan.newerThanMillis(historyImported = false, highWaterMillis = 5_000L))
        assertNull(InboxScan.newerThanMillis(historyImported = true, highWaterMillis = 0L))
    }

    @Test
    fun bank_only_full_scan_then_mno_needs_full_history() {
        // Reported bug: bank-first catch-up marks high water; enabling M-Pesa must re-read.
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.KCB, MoneySource.MPESA),
                alreadyScanned = setOf(MoneySource.KCB)
            )
        )
    }

    @Test
    fun replacing_bank_with_mno_needs_full_history() {
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA),
                alreadyScanned = setOf(MoneySource.KCB)
            )
        )
    }

    @Test
    fun mno_only_then_bank_needs_full_history() {
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA, MoneySource.EQUITY),
                alreadyScanned = setOf(MoneySource.MPESA)
            )
        )
    }

    @Test
    fun second_mno_after_first_mno_needs_full_history() {
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY),
                alreadyScanned = setOf(MoneySource.MPESA)
            )
        )
    }

    @Test
    fun already_scanned_rails_stay_incremental() {
        assertFalse(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.KCB, MoneySource.MPESA),
                alreadyScanned = setOf(MoneySource.KCB, MoneySource.MPESA)
            )
        )
    }

    @Test
    fun narrowing_enabled_to_already_scanned_subset_stays_incremental() {
        // Turning a bank off after both were scanned must not re-pull the whole inbox.
        assertFalse(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA),
                alreadyScanned = setOf(MoneySource.KCB, MoneySource.MPESA)
            )
        )
    }

    @Test
    fun force_flag_always_reads_full_history() {
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = true,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA),
                alreadyScanned = setOf(MoneySource.MPESA)
            )
        )
    }

    @Test
    fun first_wallet_rails_pass_reads_full_history() {
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = false,
                enabled = setOf(MoneySource.KCB),
                alreadyScanned = emptySet()
            )
        )
    }

    @Test
    fun upgrade_with_empty_scanned_set_re_reads_once() {
        // Pre-v1 installs only had the boolean; empty scanned ⇒ treat enabled as missing.
        assertTrue(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = setOf(MoneySource.MPESA, MoneySource.KCB),
                alreadyScanned = emptySet()
            )
        )
    }

    @Test
    fun empty_enabled_does_not_claim_missing_rails() {
        // Import bails earlier when enabled is empty; decision stays false here.
        assertFalse(
            InboxScan.needsFullHistory(
                forceFullHistory = false,
                walletRailsScanDone = true,
                enabled = emptySet(),
                alreadyScanned = setOf(MoneySource.KCB)
            )
        )
    }

    @Test
    fun later_scan_starts_an_overlap_before_the_newest_message() {
        val newest = 10 * 60 * 1000L
        assertEquals(
            newest - InboxScan.OVERLAP_MS,
            InboxScan.newerThanMillis(historyImported = true, highWaterMillis = newest)
        )
    }

    @Test
    fun overlap_does_not_go_below_zero() {
        assertEquals(
            0L,
            InboxScan.newerThanMillis(historyImported = true, highWaterMillis = 30_000L)
        )
    }

    @Test
    fun only_a_recent_confirmation_can_match_a_pending_dial() {
        val now = 1_000_000L
        assertTrue(InboxScan.mayMatchPendingDial(now, nowMillis = now))
        assertFalse(
            InboxScan.mayMatchPendingDial(
                timestampMillis = now - InboxScan.PENDING_MATCH_MS - 1,
                nowMillis = now
            )
        )
    }

    @Test
    fun receipt_code_is_the_leading_token() {
        assertEquals(
            "THX7K2LM9P",
            MpesaSmsParser.receiptCode(
                "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN."
            )
        )
        assertNull(MpesaSmsParser.receiptCode("Confirmed without a code"))
    }
}

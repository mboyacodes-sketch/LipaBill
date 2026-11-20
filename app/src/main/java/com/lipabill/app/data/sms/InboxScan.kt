package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MoneySource

/**
 * Decides how far back an inbox refresh has to look.
 * The first successful scan reads the whole inbox. Later scans start just
 * before the newest message already considered, so a delayed SMS is not missed.
 */
internal object InboxScan {

    /** Re-read this far behind the high-water mark for out-of-order inbox writes. */
    const val OVERLAP_MS = 2 * 60 * 1000L

    /**
     * Pending dials only live for 45 minutes ([com.lipabill.app.data.repository.MerchantDirectory]).
     * Older confirmations have nothing to attach.
     */
    const val PENDING_MATCH_MS = 45 * 60 * 1000L

    fun newerThanMillis(historyImported: Boolean, highWaterMillis: Long): Long? {
        if (!historyImported || highWaterMillis <= 0L) return null
        return (highWaterMillis - OVERLAP_MS).coerceAtLeast(0L)
    }

    /**
     * Full inbox when forced, when no full pass has finished yet, or when [enabled]
     * includes a rail that was not part of a prior full pass (e.g. bank-only first,
     * then M-Pesa — older MNO SMS sit behind the high-water mark).
     */
    fun needsFullHistory(
        forceFullHistory: Boolean,
        walletRailsScanDone: Boolean,
        enabled: Set<MoneySource>,
        alreadyScanned: Set<MoneySource>
    ): Boolean =
        forceFullHistory ||
            !walletRailsScanDone ||
            enabled.any { it !in alreadyScanned }

    fun mayMatchPendingDial(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val age = nowMillis - timestampMillis
        return age <= PENDING_MATCH_MS
    }
}

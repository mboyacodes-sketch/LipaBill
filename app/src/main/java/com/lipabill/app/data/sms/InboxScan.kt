package com.lipabill.app.data.sms

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

    fun mayMatchPendingDial(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val age = nowMillis - timestampMillis
        return age <= PENDING_MATCH_MS
    }
}

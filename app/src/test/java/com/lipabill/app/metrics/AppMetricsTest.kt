package com.lipabill.app.metrics

import com.lipabill.app.ussd.RepeatOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppMetricsTest {

    @Test
    fun payment_finish_uses_an_outcome_enum() {
        assertEquals("completed", paymentFinishResult(RepeatOutcome.COMPLETED_TO_PIN))
        assertEquals("cancelled", paymentFinishResult(RepeatOutcome.USER_CANCELLED))
        assertEquals("wrong_menu", paymentFinishResult(RepeatOutcome.ABORTED_MISMATCH))
        assertEquals("failed", paymentFinishResult(RepeatOutcome.ABORTED_ERROR))
        assertEquals("failed", paymentFinishResult(RepeatOutcome.AUTH_FAILED))
        assertNull(paymentFinishResult(RepeatOutcome.MANUAL_COPY))
    }

    @Test
    fun ledger_refresh_hides_how_many_rows_were_added() {
        assertEquals("unchanged", ledgerRefreshResult(0))
        assertEquals("added", ledgerRefreshResult(1))
        assertEquals("added", ledgerRefreshResult(40))
    }
}

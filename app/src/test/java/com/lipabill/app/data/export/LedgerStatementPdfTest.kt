package com.lipabill.app.data.export

import com.lipabill.app.testTransaction
import com.lipabill.app.data.model.MoneySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatementPdfTest {

    @Test
    fun pdf_export_only_for_stretches_of_30_days_or_less() {
        assertFalse(canExportStatementPdf(0))
        assertTrue(canExportStatementPdf(1))
        assertTrue(canExportStatementPdf(7))
        assertTrue(canExportStatementPdf(30))
        assertFalse(canExportStatementPdf(31))
        assertFalse(canExportStatementPdf(90))
    }

    @Test
    fun source_badge_names_every_rail_in_the_statement() {
        assertEquals("Fuliza", statementSourceBadge(emptyList(), fuliza = true))
        assertEquals(
            "KCB",
            statementSourceBadge(
                listOf(tx(MoneySource.MPESA), tx(MoneySource.KCB)),
                sourceFilter = MoneySource.KCB
            )
        )
        assertEquals("M-Pesa", statementSourceBadge(listOf(tx(MoneySource.MPESA))))
        assertEquals(
            "KCB+M-Pesa",
            statementSourceBadge(listOf(tx(MoneySource.MPESA), tx(MoneySource.KCB)))
        )
        assertEquals(
            "3 rails",
            statementSourceBadge(
                listOf(tx(MoneySource.MPESA), tx(MoneySource.KCB), tx(MoneySource.EQUITY))
            )
        )
    }

    private fun tx(source: MoneySource) = testTransaction(
        code = "T${source.name}",
        counterpartyName = "Test",
        timestampMillis = 1L,
        cost = 0.0,
        rawBody = "x",
        source = source
    )
}

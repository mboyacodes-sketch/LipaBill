package com.lipabill.app.data.export

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LedgerCsvTest {

    @Test
    fun rows_are_oldest_first_with_header() {
        val csv = ledgerCsv(
            listOf(
                tx("QK2", TransactionType.PAYBILL, "KPLC", 900.0, LocalDateTime.of(2026, 11, 5, 9, 30), cost = 0.0),
                tx("QK1", TransactionType.RECEIVED, "Boss", 9_000.0, LocalDateTime.of(2026, 11, 1, 18, 5))
            ),
            ZoneOffset.UTC
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(
            "Date,Time,Code,Type,Direction,Counterparty,Phone,Amount,Fee,Balance,Reversed",
            lines[0]
        )
        assertEquals(3, lines.size)
        assertEquals("2026-11-01", lines[1].substringBefore(","))
        assertEquals("QK2", lines[2].split(",")[2])
        assertEquals("900.00", lines[2].split(",")[7])
    }

    @Test
    fun reversed_payments_are_marked() {
        val csv = ledgerCsv(
            listOf(
                tx("QK12ABC34D", TransactionType.SENT, "Jane", 1_500.0, LocalDateTime.of(2026, 11, 2, 8, 0)),
                tx(
                    "QK13XYZ99Z",
                    TransactionType.REVERSED,
                    null,
                    1_500.0,
                    LocalDateTime.of(2026, 11, 3, 8, 0),
                    raw = "Your original transaction QK12ABC34D in favour of JANE has been reversed."
                )
            ),
            ZoneOffset.UTC
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("yes", lines[1].substringAfterLast(","))
        assertEquals("reversal", lines[2].substringAfterLast(","))
    }

    @Test
    fun cells_that_spreadsheets_would_run_are_neutralised() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, csvCell("=HYPERLINK(\"x\")"))
        assertEquals("'@SUM(A1)", csvCell("@SUM(A1)"))
        assertEquals("'+254712345678", csvCell("+254712345678"))
        assertEquals("\"Mama Mboga, Kawangware\"", csvCell("Mama Mboga, Kawangware"))
        assertEquals("Plain", csvCell("Plain"))
    }

    private fun tx(
        code: String,
        type: TransactionType,
        name: String?,
        amount: Double,
        at: LocalDateTime,
        cost: Double? = null,
        raw: String = ""
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = null,
        timestampMillis = at.toInstant(ZoneOffset.UTC).toEpochMilli(),
        balance = null,
        cost = cost,
        rawBody = raw
    )
}

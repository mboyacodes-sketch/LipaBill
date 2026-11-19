package com.lipabill.app.data.tickets

import com.lipabill.app.data.local.dao.TicketDao
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.repository.TicketRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Talanta-style ticket SMS (fictional) and the same ticket's file, saved in either order. */
class TicketSmsMergeTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 11, 18)

    private val sms = """
        Talanta Sports City: Your ticket is confirmed. FC Bayern Munich vs AFC Ajax, Tue 05 Jan 2027 20:15,
        Raila Odinga International Stadium. Category: Regular. Gate 4, Block C, Row 12, Seat 18.
        Ticket No: TSC-27-000123. View your QR: https://talantasportscity.com/t/AB12CD. One scan only.
    """.trimIndent()

    /** What the PDF / screenshot import yields for the same ticket. */
    private val fileDraft = TicketImport(
        title = "FC Bayern Munich vs AFC Ajax",
        venue = "Raila Odinga International Stadium, Nairobi",
        startsAtMillis = LocalDateTime.of(2027, 1, 5, 20, 15).atZone(zone).toInstant().toEpochMilli(),
        seatOrTier = "Gate 4 · Section C · Row 12 · Seat 18",
        barcodeValue = "TSC-SAMPLE-27-000123-NOT-VALID",
        orderId = "ORD-884201",
        notes = "Guest: Jane Doe · Tier: Regular · Doors: 11:00 AM · Ticket no: TSC-27-000123",
        source = TicketSource.E_TICKET
    )

    @Test
    fun smsBecomesAPassWithPendingCode() {
        val draft = EventTicketSms.parse(sms, today, zone)
        assertEquals("FC Bayern Munich vs AFC Ajax", draft.title)
        assertTrue(draft.venue!!.startsWith("Raila Odinga International Stadium"))
        assertEquals(fileDraft.startsAtMillis, draft.startsAtMillis)
        assertTrue(EventTicketSms.isPending(draft.barcodeValue))
        assertEquals("https://talantasportscity.com/t/AB12CD", draft.notes.notesValue(EventTicketSms.LINK_KEY))
        assertEquals("TSC-27-000123", draft.orderId)
        assertTrue(draft.seatOrTier!!.contains("Seat 18"))
    }

    @Test
    fun railSmsStaysOnTheBookingPath() {
        assertTrue(EventTicketSms.looksLikeRailBooking("Madaraka Express: Ref No KRCX1Y2Z3 Train Nairobi to Mombasa E2"))
    }

    @Test
    fun smsThenFileMakesOnePassWithTheRealCode() = runBlocking {
        val dao = FakeTicketDao()
        val repo = TicketRepository(dao)
        val id = repo.addOrMerge(EventTicketSms.parse(sms, today, zone))!!
        assertEquals(id, repo.addOrMerge(fileDraft))
        val saved = dao.rows.single()
        assertEquals("TSC-SAMPLE-27-000123-NOT-VALID", saved.barcodeValue)
        assertEquals("Jane Doe", saved.notes.notesValue("Guest"))
        assertEquals("https://talantasportscity.com/t/AB12CD", saved.notes.notesValue(EventTicketSms.LINK_KEY))
    }

    @Test
    fun fileThenSmsKeepsTheCodeAndAddsTheLink() = runBlocking {
        val dao = FakeTicketDao()
        val repo = TicketRepository(dao)
        val id = repo.addOrMerge(fileDraft)!!
        assertEquals(id, repo.addOrMerge(EventTicketSms.parse(sms, today, zone)))
        val saved = dao.rows.single()
        assertEquals("TSC-SAMPLE-27-000123-NOT-VALID", saved.barcodeValue)
        assertEquals("ORD-884201", saved.orderId)
        assertEquals("https://talantasportscity.com/t/AB12CD", saved.notes.notesValue(EventTicketSms.LINK_KEY))
    }

    @Test
    fun differentTicketsStaySeparate() = runBlocking {
        val dao = FakeTicketDao()
        val repo = TicketRepository(dao)
        repo.addOrMerge(fileDraft)
        repo.addOrMerge(EventTicketSms.parse(sms.replace("TSC-27-000123", "TSC-27-000999"), today, zone))
        assertEquals(2, dao.rows.size)
    }
}

/** Just enough of the ticket table for repository tests. */
private class FakeTicketDao : TicketDao {
    val rows = mutableListOf<TicketEntity>()
    private var next = 1L
    override fun observeAll(nowMillis: Long): Flow<List<TicketEntity>> = flowOf(rows.toList())
    override fun observeById(id: Long): Flow<TicketEntity?> = flowOf(rows.firstOrNull { it.id == id })
    override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun getByBarcodeValue(value: String) = rows.firstOrNull { it.barcodeValue == value }
    override suspend fun getByBoardingBarcodeValue(value: String) = rows.firstOrNull { it.boardingBarcodeValue == value }
    override suspend fun getByReturnBoardingBarcodeValue(value: String) = rows.firstOrNull { it.returnBoardingBarcodeValue == value }
    override suspend fun eventTickets() = rows.filter { !it.expectsBoardingPass }
    override suspend fun getByOrderId(orderId: String) = rows.firstOrNull { it.orderId == orderId }
    override suspend fun insert(entity: TicketEntity): Long {
        val id = next++
        rows += entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: TicketEntity) {
        rows.replaceAll { if (it.id == entity.id) entity else it }
    }
    override suspend fun updateStatus(id: Long, status: TicketStatus) {
        rows.replaceAll { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun updateStartsAt(id: Long, startsAtMillis: Long?) {
        rows.replaceAll { if (it.id == id) it.copy(startsAtMillis = startsAtMillis) else it }
    }
    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }
}

package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lipabill.app.data.local.entity.DocumentExtractionEntity
import com.lipabill.app.data.local.entity.PassDocumentEntity

@Dao
interface PassDocumentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: PassDocumentEntity)

    @Query("SELECT * FROM pass_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PassDocumentEntity?

    @Query("SELECT * FROM pass_documents WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): PassDocumentEntity?

    @Query("SELECT * FROM pass_documents WHERE ticketId = :ticketId ORDER BY importedAtMillis")
    suspend fun forTicket(ticketId: Long): List<PassDocumentEntity>

    /** Drafts never saved as a pass, older than [beforeMillis]. */
    @Query("SELECT * FROM pass_documents WHERE ticketId IS NULL AND importedAtMillis < :beforeMillis")
    suspend fun unlinkedBefore(beforeMillis: Long): List<PassDocumentEntity>

    @Query("UPDATE pass_documents SET ticketId = :ticketId WHERE id = :id")
    suspend fun linkToTicket(id: String, ticketId: Long)

    @Query("DELETE FROM pass_documents WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraction(extraction: DocumentExtractionEntity)

    @Query(
        "SELECT * FROM document_extractions WHERE documentId = :documentId " +
            "ORDER BY createdAtMillis DESC LIMIT 1"
    )
    suspend fun latestExtraction(documentId: String): DocumentExtractionEntity?

    @Query("DELETE FROM document_extractions WHERE documentId = :documentId")
    suspend fun deleteExtractions(documentId: String)
}

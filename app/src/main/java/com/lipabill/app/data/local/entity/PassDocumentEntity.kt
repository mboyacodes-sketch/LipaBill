package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Original ticket file (PDF / photo), kept encrypted in app-private storage.
 * Exists before a pass does, so a failed or cancelled read can be retried.
 * The user's file name is not kept: ticket files are often named after the
 * passenger or booking reference.
 */
@Entity(
    tableName = "pass_documents",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index(value = ["ticketId"])
    ]
)
data class PassDocumentEntity(
    @PrimaryKey val id: String,
    /** Pass this file became, once saved. Null while it is only a draft. */
    val ticketId: Long? = null,
    /** File name under the app's private pass-document folder. */
    val storedName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    /** Of the plaintext bytes — the same file imported twice is caught here. */
    val sha256: String,
    val importedAtMillis: Long
)

/**
 * What was read from a [PassDocumentEntity]: each field with its value, confidence and source.
 * [extractionVersion] lets a later reader re-run old documents.
 */
@Entity(
    tableName = "document_extractions",
    indices = [Index(value = ["documentId"])]
)
data class DocumentExtractionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    /** [com.lipabill.app.data.tickets.intelligence.PassKind] name. */
    val documentType: String,
    val fieldsJson: String,
    val confidence: Float,
    val extractionVersion: Int,
    val createdAtMillis: Long
)

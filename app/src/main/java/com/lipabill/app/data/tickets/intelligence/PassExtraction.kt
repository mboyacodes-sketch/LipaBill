package com.lipabill.app.data.tickets.intelligence

import org.json.JSONObject

/** What a document is. Flights are recognised only to be turned away (not supported yet). */
enum class PassKind { EVENT, RAIL, OTHER }

/**
 * Where a field's value came from. Lets review say "read from the PDF" vs "guessed".
 * Phase 4 local-model provenance is deferred — do not add TEMPLATE / LOCAL_MODEL until
 * an on-device model path ships; unknown names in stored JSON are skipped on load.
 */
enum class ExtractionSource {
    PDF_TEXT,
    OCR,
    BARCODE,
    WALLET_PASS,
    USER_INPUT;

    /** Pixel-decoded or Wallet barcode — safe to treat as gate-ready without a typed-out warning. */
    val isGateDecoded: Boolean
        get() = this == BARCODE || this == WALLET_PASS

    companion object {
        fun parseOrNull(name: String): ExtractionSource? =
            entries.firstOrNull { it.name == name }
    }
}

/** Fields a pass review shows. Values are display strings; dates are epoch millis as text. */
enum class PassField { TITLE, VENUE, STARTS_AT, ENDS_AT, SEAT_OR_TIER, TIER, GUEST, ORDER_ID, BARCODE }

data class ExtractedField(
    val value: String?,
    val confidence: Float,
    val source: ExtractionSource,
    val requiresReview: Boolean = value != null && confidence < ConfidencePolicy.REVIEW_BELOW
)

data class ClassificationResult(
    val kind: PassKind,
    val confidence: Float,
    /** Short, content-free reasons ("rail keywords", "chosen when adding") — never ticket text. */
    val evidence: List<String>,
    val needsUserConfirmation: Boolean
)

/** Thresholds from the spec. Starting points, not calibrated accuracy claims. */
object ConfidencePolicy {
    const val HIGH = 0.90f
    const val REVIEW_BELOW = 0.70f
    const val CLASSIFY_CONFIRM_BELOW = 0.70f
}

/**
 * One read of one document. Persisted as JSON in `document_extractions`;
 * bump [VERSION] when the readers change so old rows can be re-read.
 */
data class PassExtraction(
    val classification: ClassificationResult,
    val fields: Map<PassField, ExtractedField>
) {
    /** Mean confidence of the fields that were found. 0 when nothing was. */
    val confidence: Float
        get() = fields.values.filter { it.value != null }
            .map { it.confidence }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat() ?: 0f

    fun needsReview(field: PassField): Boolean = fields[field]?.requiresReview == true

    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("kind", classification.kind.name)
        put("kindConfidence", classification.confidence.toDouble())
        put("evidence", org.json.JSONArray(classification.evidence))
        put("confirmKind", classification.needsUserConfirmation)
        val f = JSONObject()
        fields.forEach { (key, field) ->
            f.put(
                key.name,
                JSONObject().apply {
                    put("value", field.value ?: JSONObject.NULL)
                    put("confidence", field.confidence.toDouble())
                    put("source", field.source.name)
                    put("review", field.requiresReview)
                }
            )
        }
        put("fields", f)
    }.toString()

    companion object {
        const val VERSION = 1

        fun fromJson(json: String): PassExtraction? = runCatching {
            val o = JSONObject(json)
            val kind = PassKind.entries.firstOrNull { it.name == o.getString("kind") }
                ?: return@runCatching null
            val evidence = o.getJSONArray("evidence").let { a ->
                List(a.length()) { i -> a.getString(i) }
            }
            val classification = ClassificationResult(
                kind = kind,
                confidence = o.getDouble("kindConfidence").toFloat(),
                evidence = evidence,
                needsUserConfirmation = o.getBoolean("confirmKind")
            )
            val f = o.optJSONObject("fields") ?: JSONObject()
            val fields = f.keys().asSequence().mapNotNull { name ->
                val key = PassField.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                val v = f.optJSONObject(name) ?: return@mapNotNull null
                val source = ExtractionSource.parseOrNull(v.optString("source", ""))
                    ?: return@mapNotNull null
                key to ExtractedField(
                    value = if (v.isNull("value")) null else v.optString("value").ifBlank { null },
                    confidence = v.optDouble("confidence", 0.0).toFloat(),
                    source = source,
                    requiresReview = v.optBoolean("review", true)
                )
            }.toMap()
            PassExtraction(classification, fields)
        }.getOrNull()
    }
}

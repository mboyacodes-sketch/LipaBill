package com.lipabill.app.data.tickets.intelligence

import org.json.JSONObject

/** What a document is. Flights are recognised only to be turned away (not supported yet). */
enum class PassKind { EVENT, RAIL, OTHER }

/** Where a field's value came from. Lets review say "read from the PDF" vs "guessed". */
enum class ExtractionSource {
    PDF_TEXT,
    OCR,
    BARCODE,
    WALLET_PASS,
    TEMPLATE,
    LOCAL_MODEL,
    USER_INPUT
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
            val evidence = o.getJSONArray("evidence").let { a -> List(a.length()) { a.getString(it) } }
            val classification = ClassificationResult(
                kind = PassKind.valueOf(o.getString("kind")),
                confidence = o.getDouble("kindConfidence").toFloat(),
                evidence = evidence,
                needsUserConfirmation = o.getBoolean("confirmKind")
            )
            val f = o.getJSONObject("fields")
            val fields = f.keys().asSequence().mapNotNull { name ->
                val key = runCatching { PassField.valueOf(name) }.getOrNull() ?: return@mapNotNull null
                val v = f.getJSONObject(name)
                key to ExtractedField(
                    value = if (v.isNull("value")) null else v.getString("value"),
                    confidence = v.getDouble("confidence").toFloat(),
                    source = ExtractionSource.valueOf(v.getString("source")),
                    requiresReview = v.getBoolean("review")
                )
            }.toMap()
            PassExtraction(classification, fields)
        }.getOrNull()
    }
}

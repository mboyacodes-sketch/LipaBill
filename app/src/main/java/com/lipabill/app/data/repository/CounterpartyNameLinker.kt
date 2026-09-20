package com.lipabill.app.data.repository

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ussd.UssdMenuBuilder

/**
 * Links counterparties that share a name so a known phone can fill txs missing one.
 */
object CounterpartyNameLinker {

    private const val MIN_NAME_LEN = 3

    fun normalizeName(raw: String?): String? {
        val trimmed = raw?.trim()?.replace("\\s+".toRegex(), " ").orEmpty()
        if (trimmed.length < MIN_NAME_LEN) return null
        // Ignore pure digit "names" (tills / paybills stored as name).
        if (trimmed.all { it.isDigit() || it.isWhitespace() }) return null
        return trimmed.uppercase()
    }

    /**
     * Build name → phone from rows that already have a dialable phone.
     * First write wins (pass newest-first for most recent phone).
     */
    fun buildPhoneDirectory(
        names: List<String?>,
        phones: List<String?>
    ): Map<String, String> {
        require(names.size == phones.size)
        val out = LinkedHashMap<String, String>()
        for (i in names.indices) {
            val key = normalizeName(names[i]) ?: continue
            if (out.containsKey(key)) continue
            val phone = UssdMenuBuilder.normalizePhoneNumber(phones[i]) ?: continue
            out[key] = phone
        }
        return out
    }

    fun buildPhoneDirectoryFromTransactions(txs: List<MpesaTransaction>): Map<String, String> =
        buildPhoneDirectory(
            names = txs.map { it.counterpartyName },
            phones = txs.map { it.counterpartyPhone }
        )

    fun enrich(tx: MpesaTransaction, directory: Map<String, String>): MpesaTransaction {
        if (!tx.counterpartyPhone.isNullOrBlank()) return tx
        val key = normalizeName(tx.counterpartyName) ?: return tx
        val linked = directory[key] ?: return tx
        return tx.copy(counterpartyPhone = linked)
    }

    fun enrichAll(txs: List<MpesaTransaction>, directory: Map<String, String>? = null): List<MpesaTransaction> {
        val dir = directory ?: buildPhoneDirectoryFromTransactions(txs)
        if (dir.isEmpty()) return txs
        return txs.map { enrich(it, dir) }
    }
}

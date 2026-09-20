package com.lipabill.app.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ussd.UssdMenuBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device phone-book lookup for Send/Pay recipient search.
 * Results are normalized to dialable M-Pesa phones when possible.
 */
object PhoneBookSearcher {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Search contacts by display name or phone digits.
     * Empty / very short queries return nothing (avoid dumping the whole book).
     */
    suspend fun search(
        context: Context,
        query: String,
        limit: Int = 20
    ): List<SendContact> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !hasPermission(context)) return@withContext emptyList()

        val cr = context.contentResolver
        val selection = buildString {
            append("(")
            append("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?")
            append(" OR ")
            append("${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?")
            append(")")
        }
        val like = "%$trimmed%"
        val digitsQuery = trimmed.filter { it.isDigit() }
        val args = arrayOf(like, if (digitsQuery.length >= 2) "%$digitsQuery%" else like)

        val seen = LinkedHashSet<String>()
        val out = ArrayList<SendContact>(limit)

        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && out.size < limit) {
                val rawPhone = if (numberIdx >= 0) cursor.getString(numberIdx)?.trim().orEmpty() else ""
                val normalized = UssdMenuBuilder.normalizePhoneNumber(rawPhone) ?: continue
                if (!seen.add(normalized)) continue
                val name = if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                out.add(
                    SendContact(
                        transactionId = 0L,
                        name = name,
                        phone = rawPhone.ifBlank { normalized },
                        normalizedPhone = normalized,
                        fromPhoneBook = true
                    )
                )
            }
        }
        out
    }
}

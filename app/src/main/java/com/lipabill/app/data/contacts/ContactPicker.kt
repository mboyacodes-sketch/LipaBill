package com.lipabill.app.data.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ussd.UssdMenuBuilder

/**
 * One-shot Android Contact Picker. No [android.Manifest.permission.READ_CONTACTS]:
 * the picker grants a temporary URI for the number the user chose.
 */
object ContactPicker {

    const val UNUSABLE_MESSAGE =
        "That number isn’t one LipaBill can send to. Pick another, or type it."
    const val UNAVAILABLE_MESSAGE =
        "Contacts aren’t available on this phone. Type the number instead."

    fun pickIntent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    fun read(context: Context, uri: Uri): Picked {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return Picked.Empty
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
            val number = if (numberIdx >= 0) cursor.getString(numberIdx) else null
            return fromFields(name, number)
        }
        return Picked.Empty
    }

    fun fromFields(displayName: String?, rawNumber: String?): Picked {
        val raw = rawNumber?.trim().orEmpty()
        if (raw.isEmpty()) return Picked.Empty
        val normalized = UssdMenuBuilder.normalizePhoneNumber(raw)
            ?: return Picked.Unusable(raw)
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        return Picked.Ok(
            SendContact(
                transactionId = 0L,
                name = name,
                phone = raw,
                normalizedPhone = normalized,
                fromPhoneBook = true
            )
        )
    }

    sealed class Picked {
        data class Ok(val contact: SendContact) : Picked()
        data class Unusable(val rawNumber: String) : Picked()
        data object Empty : Picked()
    }
}

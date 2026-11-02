package com.lipabill.app.data.contacts

import com.lipabill.app.resolveRepoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPickerTest {

    @Test
    fun kenyan_mobile_becomes_a_send_contact() {
        val picked = ContactPicker.fromFields("Jane", "0712 345 678")
        val ok = picked as ContactPicker.Picked.Ok
        assertEquals("Jane", ok.contact.name)
        assertEquals("0712345678", ok.contact.normalizedPhone)
        assertTrue(ok.contact.fromPhoneBook)
    }

    @Test
    fun international_prefix_normalizes() {
        val picked = ContactPicker.fromFields("Jane", "+254712345678")
        val ok = picked as ContactPicker.Picked.Ok
        assertEquals("0712345678", ok.contact.normalizedPhone)
    }

    @Test
    fun blank_number_is_empty() {
        assertEquals(ContactPicker.Picked.Empty, ContactPicker.fromFields("Jane", "  "))
        assertEquals(ContactPicker.Picked.Empty, ContactPicker.fromFields(null, null))
    }

    @Test
    fun non_mpesa_number_is_unusable() {
        val picked = ContactPicker.fromFields("Shop", "123")
        assertTrue(picked is ContactPicker.Picked.Unusable)
    }

    @Test
    fun play_manifest_does_not_declare_read_contacts() {
        val manifest = resolveRepoFile("app/src/main/AndroidManifest.xml")!!.readText()
        assertFalse(manifest.contains("android.permission.READ_CONTACTS"))
        assertTrue(manifest.contains("vnd.android.cursor.dir/phone_v2"))
    }
}

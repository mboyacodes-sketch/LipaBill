package com.lipabill.app.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class DataAccessRationaleTest {

    @Test
    fun camera_group_explains_ticket_scanning() {
        val copy = DataAccessRationale.forPermissionGroup("android.permission-group.CAMERA")
        assertEquals(DataAccessRationale.CAMERA, copy)
    }

    @Test
    fun microphone_and_location_say_they_are_unused() {
        assertEquals(
            DataAccessRationale.MICROPHONE,
            DataAccessRationale.forPermissionGroup("android.permission-group.MICROPHONE")
        )
        assertEquals(
            DataAccessRationale.LOCATION,
            DataAccessRationale.forPermissionGroup("android.permission-group.LOCATION")
        )
    }

    @Test
    fun sms_phone_contacts_and_notifications_match_the_feature() {
        assertEquals(
            DataAccessRationale.SMS,
            DataAccessRationale.forPermissionGroup("android.permission-group.SMS")
        )
        assertEquals(
            DataAccessRationale.PHONE,
            DataAccessRationale.forPermissionGroup("android.permission-group.PHONE")
        )
        assertEquals(
            DataAccessRationale.CONTACTS,
            DataAccessRationale.forPermissionGroup("android.permission-group.CONTACTS")
        )
        assertEquals(
            DataAccessRationale.NOTIFICATIONS,
            DataAccessRationale.forPermissionGroup("android.permission-group.NOTIFICATIONS")
        )
    }

    @Test
    fun missing_group_uses_the_general_explanation() {
        assertEquals(DataAccessRationale.GENERAL, DataAccessRationale.forPermissionGroup(null))
        assertEquals(DataAccessRationale.GENERAL, DataAccessRationale.forPermissionGroup(""))
    }
}

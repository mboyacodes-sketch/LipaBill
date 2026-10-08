package com.lipabill.app.ui.permissions

/**
 * Copy shown when Android opens LipaBill from the Privacy Dashboard or the
 * app's permission page (VIEW_PERMISSION_USAGE).
 */
data class DataAccessCopy(
    val title: String,
    val body: String
)

object DataAccessRationale {

    fun forPermissionGroup(groupName: String?): DataAccessCopy {
        val group = groupName.orEmpty()
        return when {
            group.endsWith("CAMERA") -> CAMERA
            group.endsWith("MICROPHONE") -> MICROPHONE
            group.endsWith("LOCATION") -> LOCATION
            group.endsWith("SMS") -> SMS
            group.endsWith("PHONE") || group.endsWith("CALL_LOG") -> PHONE
            group.endsWith("CONTACTS") -> CONTACTS
            group.endsWith("NOTIFICATIONS") -> NOTIFICATIONS
            else -> GENERAL
        }
    }

    val CAMERA = DataAccessCopy(
        title = "Camera scans a pass",
        body = "LipaBill opens the camera only while you scan a ticket or boarding-pass code. " +
            "It does not record video or save photos."
    )

    val MICROPHONE = DataAccessCopy(
        title = "Microphone is not used",
        body = "LipaBill does not record audio."
    )

    val LOCATION = DataAccessCopy(
        title = "Location is not used",
        body = "LipaBill does not read this phone's location. A venue name on a pass comes from the ticket file."
    )

    val SMS = DataAccessCopy(
        title = "SMS builds M-Pesa history",
        body = "LipaBill reads Safaricom M-Pesa confirmation messages to keep your ledger on this phone. " +
            "It does not send SMS."
    )

    val PHONE = DataAccessCopy(
        title = "Phone starts a payment you confirm",
        body = "LipaBill places the M-Pesa call and checks which SIM is Safaricom after you confirm a payment. " +
            "It does not read call history."
    )

    val CONTACTS = DataAccessCopy(
        title = "Contacts match a name you type",
        body = "LipaBill looks up people saved on this phone when you type a recipient. " +
            "You can type a number without contacts."
    )

    val NOTIFICATIONS = DataAccessCopy(
        title = "Alerts for payments and confirmations",
        body = "LipaBill can notify you when an M-Pesa confirmation arrives, " +
            "and when a payment you started needs you."
    )

    val GENERAL = DataAccessCopy(
        title = "Why LipaBill uses this access",
        body = "LipaBill uses device access for the feature you opened: M-Pesa history, " +
            "a payment you confirm, a contact lookup, or a ticket scan. That data stays on this phone."
    )
}

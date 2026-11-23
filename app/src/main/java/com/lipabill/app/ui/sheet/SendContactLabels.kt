package com.lipabill.app.ui.sheet

import com.lipabill.app.data.repository.SendContact

fun SendContact.sheetName(): String = name ?: normalizedPhone

fun SendContact.sameParty(other: SendContact?): Boolean =
    other != null &&
        normalizedPhone == other.normalizedPhone &&
        fromPhoneBook == other.fromPhoneBook

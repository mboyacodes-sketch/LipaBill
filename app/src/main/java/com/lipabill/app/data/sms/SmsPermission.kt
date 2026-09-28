package com.lipabill.app.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun Context.hasMpesaSmsPermission(): Boolean {
    val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
    val receive = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
    return read == PackageManager.PERMISSION_GRANTED &&
        receive == PackageManager.PERMISSION_GRANTED
}

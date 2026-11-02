package com.lipabill.app.ui.sheet

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lipabill.app.data.contacts.ContactPicker
import com.lipabill.app.data.repository.SendContact

@Composable
fun rememberPhoneContactPicker(
    onPicked: (SendContact) -> Unit,
    onRejected: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data
        if (uri == null) {
            onRejected(ContactPicker.UNUSABLE_MESSAGE)
            return@rememberLauncherForActivityResult
        }
        when (val picked = ContactPicker.read(context, uri)) {
            is ContactPicker.Picked.Ok -> onPicked(picked.contact)
            is ContactPicker.Picked.Unusable -> onRejected(ContactPicker.UNUSABLE_MESSAGE)
            ContactPicker.Picked.Empty -> onRejected(ContactPicker.UNUSABLE_MESSAGE)
        }
    }
    return {
        runCatching { launcher.launch(ContactPicker.pickIntent()) }
            .onFailure { onRejected(ContactPicker.UNAVAILABLE_MESSAGE) }
    }
}

package com.lipabill.app.ui.util

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.lipabill.app.ui.privacy.LocalRecordingPrivacy

/**
 * While a LipaBill keyboard is on screen, that window is left out of screenshots,
 * recents, and screen capture. Debug recording mode skips this so a blurred pad
 * can still be filmed.
 */
@Composable
fun SecureKeyboardWindow() {
    val view = LocalView.current
    val recording = LocalRecordingPrivacy.current
    DisposableEffect(view, recording) {
        if (recording) return@DisposableEffect onDispose {}
        var applied = false
        fun apply() {
            if (applied) return
            val root = view.rootView
            val params = root.layoutParams as? WindowManager.LayoutParams ?: return
            if (params.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) return
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE
            if (updateWindow(view, root, params)) applied = true
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = apply()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) apply()
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
            if (!applied) return@onDispose
            val root = view.rootView
            val current = root.layoutParams as? WindowManager.LayoutParams ?: return@onDispose
            current.flags = current.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            updateWindow(view, root, current)
        }
    }
}

private fun updateWindow(view: View, root: View, params: WindowManager.LayoutParams): Boolean {
    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return runCatching { wm.updateViewLayout(root, params) }.isSuccess
}

package com.lipabill.app.ui.sheet

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.EdgeToEdge
import androidx.activity.SystemBarStyle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Expands a bottom sheet to nearly full screen and re-applies after layout so
 * Compose content that measures late (Pay/Send) still fills on first open.
 */
fun BottomSheetDialog.expandForComposeContent() {
    window?.let { enableSheetEdgeToEdge(it) }
    setOnShowListener { dlg ->
        val dialog = dlg as BottomSheetDialog
        dialog.window?.let { enableSheetEdgeToEdge(it) }
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return@setOnShowListener

        sheet.setBackgroundResource(android.R.color.transparent)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val behavior = BottomSheetBehavior.from(sheet)
        behavior.skipCollapsed = true
        behavior.isFitToContents = false
        behavior.expandedOffset = 0
        behavior.isDraggable = true
        @Suppress("DEPRECATION")
        behavior.isShouldRemoveExpandedCorners = false
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Compose often lays out after the first measure — force expand again.
        fun reExpand() {
            sheet.requestLayout()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        sheet.post { reExpand() }
        sheet.postDelayed({ reExpand() }, 50L)
        sheet.postDelayed({ reExpand() }, 160L)
    }
}

/** Same edge-to-edge contract as the activity, so the sheet matches on Android 14 and 15. */
private fun enableSheetEdgeToEdge(window: Window) {
    EdgeToEdge.enable(
        window,
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    if (Build.VERSION.SDK_INT >= 30) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

package com.lipabill.app.ui.sheet

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Expands a bottom sheet to nearly full screen and re-applies after layout so
 * Compose content that measures late (Pay/Send) still fills on first open.
 */
fun BottomSheetDialog.expandForComposeContent() {
    window?.let { win ->
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    setOnShowListener { dlg ->
        val dialog = dlg as BottomSheetDialog
        dialog.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
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

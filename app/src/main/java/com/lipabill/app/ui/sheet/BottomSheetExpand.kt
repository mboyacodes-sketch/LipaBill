package com.lipabill.app.ui.sheet

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lipabill.app.LipaBillApp
import com.lipabill.app.ui.theme.LipaBillTheme

/** Full-height Pay/Send sheet. */
fun DialogFragment.expandedSheetDialog(): Dialog =
    newExpandedBottomSheetDialog(requireContext(), getTheme())

/** Compose content for a money sheet, using the Settings font size. */
fun Fragment.themedComposeView(content: @Composable () -> Unit): View {
    val app = requireActivity().application as LipaBillApp
    return ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val fontSize by app.fontSizeSp.collectAsStateWithLifecycle(
                initialValue = app.fontSizeSp.value
            )
            val darkMode by app.darkMode.collectAsStateWithLifecycle(
                initialValue = app.darkMode.value
            )
            LipaBillTheme(fontSizeSp = fontSize, darkTheme = darkMode, content = content)
        }
    }
}

/**
 * Pay/Send sheet that is full height before the first layout.
 * The default sheet stays collapsed until dragged, which hides the number pad.
 */
fun newExpandedBottomSheetDialog(context: Context, themeResId: Int): BottomSheetDialog {
    return object : BottomSheetDialog(context, themeResId) {
        init {
            secureMoneyWindow(window, context)
        }

        override fun onStart() {
            super.onStart()
            expandForComposeContent()
        }
    }
}

/**
 * Sizes the sheet to the window. Call from [BottomSheetDialog.onStart], after the
 * sheet view exists and before the window is added, so the keypad is on screen
 * without a later height jump.
 */
fun BottomSheetDialog.expandForComposeContent() {
    val dark = (context.applicationContext as? LipaBillApp)?.darkMode?.value == true
    window?.let {
        enableSheetEdgeToEdge(it, dark)
        secureMoneyWindow(it, context)
    }
    val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        ?: return
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
}

/**
 * Dialogs are not a ComponentActivity, so they cannot call enableEdgeToEdge().
 * Light icons when the app is in dark mode.
 */
private fun secureMoneyWindow(window: Window?, context: Context) {
    if (window == null) return
    val recording = (context.applicationContext as? LipaBillApp)?.recordingPrivacy?.value == true
    if (recording) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private fun enableSheetEdgeToEdge(window: Window, dark: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
    if (Build.VERSION.SDK_INT >= 30) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

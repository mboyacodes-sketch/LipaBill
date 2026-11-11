package com.lipabill.app.ui.sheet

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lipabill.app.LipaBillApp
import com.lipabill.app.ui.adapt.ProvideWindowWidth
import com.lipabill.app.ui.adapt.moneySheetHeightFraction
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.ui.theme.LipaBillTheme

/** Adaptive ~90% height Pay/Send sheet, flush to the bottom edge. */
fun DialogFragment.expandedSheetDialog(): Dialog =
    newExpandedBottomSheetDialog(requireContext(), getTheme())

/** Compose content for a money sheet, using the Settings font size. */
fun Fragment.themedComposeView(content: @Composable () -> Unit): View {
    val app = requireActivity().application as LipaBillApp
    return ComposeView(requireContext()).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.TOP
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val fontSize by app.fontSizeSp.collectAsStateWithLifecycle()
            val darkMode by app.darkMode.collectAsStateWithLifecycle()
            LipaBillTheme(fontSizeSp = fontSize, darkTheme = darkMode) {
                // Dialogs are their own window — measure width/height here, not from Activity.
                ProvideWindowWidth(content = content)
            }
        }
    }
}

/**
 * Pay/Send sheet: full-height container with a top gap so most of the glass shows, flush
 * on the bottom. Compose is sized to the visible band so the in-app keyboard stays on screen.
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
 * MATCH_PARENT sheet + [BottomSheetBehavior.expandedOffset] for the top gap (bottom stays
 * flush). Compose children are limited to the visible height so keypad/keyboard layout
 * against the on-screen area, not the off-screen strip below.
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
    val behavior = BottomSheetBehavior.from(sheet)
    behavior.skipCollapsed = true
    behavior.isFitToContents = false
    behavior.isDraggable = true
    @Suppress("DEPRECATION")
    behavior.isShouldRemoveExpandedCorners = false

    fun applyVisibleBand() {
        val parent = sheet.parent as? View
        val metrics = context.resources.displayMetrics
        val parentHeight = parent?.height?.takeIf { it > 0 }
            ?: window?.decorView?.height?.takeIf { it > 0 }
            ?: metrics.heightPixels
        val heightDp = (parentHeight / metrics.density).toInt().coerceAtLeast(1)
        val fraction = moneySheetHeightFraction(heightDp)
        val topGap = (parentHeight * (1f - fraction)).toInt().coerceAtLeast(0)
        val visibleHeight = (parentHeight - topGap).coerceAtLeast(1)

        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        behavior.expandedOffset = topGap
        behavior.peekHeight = parentHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Keep Compose in the on-screen band (top of the sheet), not the clipped strip below.
        val host = sheet as? ViewGroup ?: return
        host.children.forEach { child ->
            val lp = child.layoutParams
            child.layoutParams = when (lp) {
                is FrameLayout.LayoutParams -> lp.apply {
                    height = visibleHeight
                    gravity = Gravity.TOP
                }
                else -> FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    visibleHeight,
                    Gravity.TOP
                )
            }
        }
        sheet.requestLayout()
    }

    val parentReady = (sheet.parent as? View)?.height ?: 0 > 0
    if (parentReady) {
        applyVisibleBand()
    } else {
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        behavior.expandedOffset = 0
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.post { applyVisibleBand() }
    }
}

/**
 * Dialogs are not a ComponentActivity, so they cannot call enableEdgeToEdge().
 * Light icons when the app is in dark mode.
 */
private fun secureMoneyWindow(window: Window?, context: Context) {
    if (window == null) return
    val recording = (context.applicationContext as? LipaBillApp)?.recordingPrivacy?.value == true
    if (RecordingCapture.available && recording) {
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
    // In-app keyboards only — do not resize the sheet for the system IME.
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
}

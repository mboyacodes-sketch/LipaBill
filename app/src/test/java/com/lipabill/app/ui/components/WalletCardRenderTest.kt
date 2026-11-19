package com.lipabill.app.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.viewmodel.WalletCardUi
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design review only (skipped by default):
 *   WALLET_CARD_RENDER=1 ./gradlew :app:testPlayDebugUnitTest --tests '*WalletCardRender*'
 * Output: app/build/wallet-cards.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xxhdpi", application = android.app.Application::class)
class WalletCardRenderTest {

    @Test
    fun render() {
        assumeTrue(System.getenv("WALLET_CARD_RENDER")?.isNotBlank() == true)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val two = listOf(
            WalletCardUi(MoneySource.MPESA, "Safaricom · SIM 1", "6877", 15391.83, "Safaricom", 1),
            WalletCardUi(MoneySource.AIRTEL_MONEY, "Airtel · SIM 2", null, 56.0, "Airtel", 2)
        )
        activity.setContent {
            Column(Modifier.background(Color(0xFFF6F7F9)).padding(20.dp).width(360.dp)) {
                Text("Two lines")
                WalletCardStack(two, MoneySource.MPESA, false, {}, {}, {}) { _, _ -> }
                Spacer(Modifier.height(24.dp))
                for (p in listOf(0.25f, 0.45f, 0.55f, 0.8f)) {
                    Text("Shuffle ${(p * 100).toInt()}%")
                    WalletCardStack(two, MoneySource.MPESA, false, {}, {}, {}, swipePreview = p) { _, _ -> }
                    Spacer(Modifier.height(16.dp))
                }
                Text("After the swipe: Airtel in front")
                WalletCardStack(two, MoneySource.AIRTEL_MONEY, false, {}, {}, {}) { _, _ -> }
                Spacer(Modifier.height(24.dp))
                Text("One line, flipped to Fuliza")
                WalletCardStack(two.take(1), MoneySource.MPESA, true, {}, {}, {}, flipHint = "Tilt, tap for M-Pesa") { _, ink ->
                    Text("FULIZA", color = ink.highlight)
                    Spacer(Modifier.weight(1f))
                    CardLabel("OUTSTANDING", ink)
                    BalanceAmountRow(balance = 120.0, amountStyle = com.lipabill.app.ui.theme.HomeType.balance, contentColor = ink.primary)
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root: View = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1260, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.AT_MOST)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bmp = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(System.getProperty("user.dir"), "build/wallet-cards.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

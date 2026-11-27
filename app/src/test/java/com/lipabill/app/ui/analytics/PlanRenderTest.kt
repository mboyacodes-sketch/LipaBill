package com.lipabill.app.ui.analytics

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.viewmodel.BudgetPace
import com.lipabill.app.viewmodel.BudgetStatus
import com.lipabill.app.viewmodel.CategoryChange
import com.lipabill.app.viewmodel.GoalProgress
import com.lipabill.app.viewmodel.MoneyPlanUi
import com.lipabill.app.viewmodel.SafeToSpend
import com.lipabill.app.viewmodel.SavingsGoal
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.lipabill.app.data.model.TransactionType as T
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Design review only: WALLET_CARD_RENDER=1 … --tests '*PlanRender*' → app/build/plan-cards.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1200dp-xxhdpi", application = android.app.Application::class)
class PlanRenderTest {

    @Test
    fun render() {
        assumeTrue(System.getenv("WALLET_CARD_RENDER")?.isNotBlank() == true)
        val plan = MoneyPlanUi(
            safe = SafeToSpend(1250.0, 10000.0, 25000.0, 15000.0, 1, LocalDate.parse("2026-11-25"), 8, true),
            budgets = listOf(
                BudgetStatus(SpendCategory.FOOD, 8000.0, 6500.0, 13000.0, 12, BudgetPace.WATCH),
                BudgetStatus(SpendCategory.TRANSPORT, 4000.0, 1200.0, 2400.0, 12, BudgetPace.ON_TRACK),
                BudgetStatus(SpendCategory.SHOPPING, 5000.0, 5600.0, 9000.0, 12, BudgetPace.OVER)
            ),
            goals = listOf(
                GoalProgress(SavingsGoal("a", "School fees", 30000.0, 0L, LocalDate.parse("2027-01-13")), 12000.0, 2250.0),
                GoalProgress(SavingsGoal("b", "New phone", 20000.0, 0L), 20000.0, null)
            ),
            isReady = true
        )
        val changes = listOf(
            CategoryChange(SpendCategory.FOOD, 6500.0, 4200.0),
            CategoryChange(SpendCategory.TRANSPORT, 1200.0, 2100.0),
            CategoryChange(SpendCategory.BILLS, 3000.0, 0.0)
        )
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Column(
                Modifier.background(Color(0xFFF6F7F9)).padding(16.dp).width(1140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WatchingCard(
                    listOf(
                        com.lipabill.app.viewmodel.RegularChange.PriceChanged("DSTV KENYA", 2000.0, 2200.0),
                        com.lipabill.app.viewmodel.RegularChange.NewCharge("ZOOM GYM", 3000.0, com.lipabill.app.viewmodel.Cadence.MONTHLY),
                        com.lipabill.app.viewmodel.RegularChange.Stopped("NETFLIX", LocalDate.parse("2026-08-20"), 1100.0)
                    ),
                    listOf(com.lipabill.app.viewmodel.PriceTrend("NAIVAS WESTLANDS", 2200.0, 2000.0, java.time.YearMonth.of(2026, 5)))
                )
                val wrap = sampleWrap()
                WrappedTeaser(wrap, onOpen = {})
                val all = listOf<@androidx.compose.runtime.Composable () -> Unit>(
                    { ShareHub(shareKinds(wrap), wrap, showAmounts = true, hideNames = true) }
                ) + (listOf(WrapCardKind.SUMMARY) + wrapKinds(wrap)).map { kind ->
                    @androidx.compose.runtime.Composable {
                        WrapCard(kind, wrap, showAmounts = true, modifier = Modifier.width(360.dp).height(640.dp), hideNames = kind == WrapCardKind.SPOTS)
                    }
                }
                all.chunked(3).forEach { row ->
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { card ->
                            Box(Modifier.width(360.dp).height(640.dp).background(Color(0xFF070A08))) { card() }
                        }
                    }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root: View = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(3520, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.AT_MOST)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bmp = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(System.getProperty("user.dir"), "build/plan-cards.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // The real widget layout, filled the way BalanceWidget fills it (masked).
        val widget = android.widget.RemoteViews(activity.packageName, com.lipabill.app.R.layout.widget_balance).apply {
            setTextViewText(com.lipabill.app.R.id.widget_safe, "KSh •••• a day")
            setTextViewText(com.lipabill.app.R.id.widget_until, "until Wed 25 Nov (payday)")
            setTextViewText(com.lipabill.app.R.id.widget_balance, "Balance KSh ••••")
        }.apply(activity, android.widget.FrameLayout(activity))
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY)
        )
        widget.layout(0, 0, 660, 420)
        val wb = Bitmap.createBitmap(660, 420, Bitmap.Config.ARGB_8888)
        widget.draw(android.graphics.Canvas(wb))
        File(System.getProperty("user.dir"), "build/widget.png").outputStream().use {
            wb.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun sampleWrap(): com.lipabill.app.viewmodel.WrapCards {
        val zone = java.time.ZoneOffset.UTC
        var n = 0
        fun tx(day: Int, amount: Double, name: String, type: T, phone: String? = null, cost: Double = 0.0, hour: Int = 12) =
            com.lipabill.app.testTransaction(
                code = "R${n++}", type = type, amount = amount, counterpartyName = name, counterpartyPhone = phone, cost = cost,
                timestampMillis = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(zone).toEpochMilli()
            )
        val ledger = buildList {
            add(tx(1, 85_000.0, "ACME LTD", T.RECEIVED))
            listOf("Mama Wanjiru" to 9000.0, "Faith Achieng" to 7000.0, "Brian Otieno" to 4000.0).forEachIndexed { i, (who, amt) ->
                add(tx(3 + i, amt, who, T.SENT, phone = "07120000${10 + i}", cost = 50.0, hour = if (i == 0) 1 else 14))
            }
            listOf("JAVA HOUSE", "KIBANDASKI CORNER", "NAIVAS", "SHELL KILIMANI", "QUICKMART").forEachIndexed { i, shop ->
                repeat(5 - i) { r -> add(tx(6 + r * 4 + i, 900.0 + 100 * i, shop, T.BUY_GOODS)) }
            }
            add(tx(20, 2500.0, "KPLC PREPAID", T.PAYBILL, cost = 23.0))
            repeat(6) { add(tx(10 + it * 3, 400.0, "Fuliza", T.FULIZA)) }
        }
        return com.lipabill.app.viewmodel.buildMonthWrap(java.time.YearMonth.of(2026, 10), ledger, emptyMap(), zone)!!
    }
}

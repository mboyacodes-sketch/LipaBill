package com.lipabill.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.R
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.WrapCards
import com.lipabill.app.viewmodel.WrapPersonality
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Type and palette
//
// Card layouts follow the M-PESA Wrap reference. Backgrounds are solid colours from the
// app's own palette: brand navy, ink, and the pastel tiles used across Home and Insights.

/** Heavy grotesque for headlines and figures. SIL OFL, see third_party/archivo_black. */
internal val WrapDisplay = FontFamily(Font(R.font.archivo_black))

internal object WrapPalette {
    val Navy = Color(0xFF2F4A6E)
    val NavyDeep = Color(0xFF1B3B6E)
    val Blue = Color(0xFFDCE8F5)
    val Mint = Color(0xFFDCF5E8)
    val Lavender = Color(0xFFE8DCF5)
    val Rose = Color(0xFFF5DCDC)
    val Cream = Color(0xFFF3E9C8)
    val LabelBlue = Color(0xFF9BB0C9)
    /** Text on pastel cards (the app's ink). */
    val Ink = Color(0xFF111827)

    /** Highlight on navy: money out, top spot names, switches. */
    val Accent = Cream
}

internal enum class WrapCardKind(val title: String) {
    SUMMARY("Summary"),
    INTRO("Intro"),
    NUMBERS("By the numbers"),
    FLOW("The flow"),
    FEES("The quiet cost"),
    FULIZA("Fuliza"),
    PEOPLE("Your people"),
    SPOTS("Your top spots"),
    RHYTHM("Your rhythm"),
    PERSONALITY("Your money personality")
}

/** Story cards worth showing for [c], in story order. Empty ones are skipped. */
internal fun wrapKinds(c: WrapCards): List<WrapCardKind> = buildList {
    add(WrapCardKind.INTRO)
    add(WrapCardKind.NUMBERS)
    if (c.moneyIn > 0.0 || c.moneyOut > 0.0) add(WrapCardKind.FLOW)
    if (c.fees > 0.0) add(WrapCardKind.FEES)
    if (c.fuliza != null) add(WrapCardKind.FULIZA)
    if (c.topPeople.isNotEmpty()) add(WrapCardKind.PEOPLE)
    if (c.topSpots.isNotEmpty()) add(WrapCardKind.SPOTS)
    if (c.bars.isNotEmpty()) add(WrapCardKind.RHYTHM)
    add(WrapCardKind.PERSONALITY)
}

/** The five cards on the share screen, like the reference: summary first. */
internal fun shareKinds(c: WrapCards): List<WrapCardKind> = buildList {
    add(WrapCardKind.SUMMARY)
    add(WrapCardKind.NUMBERS)
    if (c.topSpots.isNotEmpty()) add(WrapCardKind.SPOTS)
    if (c.bars.isNotEmpty()) add(WrapCardKind.RHYTHM)
    add(WrapCardKind.PERSONALITY)
}

/** Solid background for each card. */
internal fun backgroundOf(kind: WrapCardKind): Color = when (kind) {
    WrapCardKind.SUMMARY, WrapCardKind.INTRO, WrapCardKind.SPOTS -> WrapPalette.Navy
    WrapCardKind.FLOW, WrapCardKind.PERSONALITY -> WrapPalette.NavyDeep
    WrapCardKind.NUMBERS -> WrapPalette.Blue
    WrapCardKind.FEES -> WrapPalette.Rose
    WrapCardKind.FULIZA -> WrapPalette.Cream
    WrapCardKind.PEOPLE -> WrapPalette.Mint
    WrapCardKind.RHYTHM -> WrapPalette.Lavender
}

/** True for the navy cards (white text); pastel cards use ink. */
internal fun isDark(kind: WrapCardKind): Boolean = when (backgroundOf(kind)) {
    WrapPalette.Navy, WrapPalette.NavyDeep -> true
    else -> false
}

/** Text colour for a card. */
internal fun inkOf(kind: WrapCardKind): Color = if (isDark(kind)) Color.White else WrapPalette.Ink

/** Main colour, used to tint the screen behind the card. */
internal fun baseOf(kind: WrapCardKind): Color = backgroundOf(kind)

// ---------------------------------------------------------------------------------------------
// Card layouts

/** Card background: one solid colour. */
@Composable
internal fun WrapCardArt(kind: WrapCardKind, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.background(backgroundOf(kind)))
}

@Composable
internal fun WrapBig(text: String, color: Color, size: TextUnit, modifier: Modifier = Modifier) =
    Text(
        text,
        color = color,
        fontSize = size,
        fontFamily = WrapDisplay,
        lineHeight = size * 1.04f,
        letterSpacing = (-0.5).sp,
        modifier = modifier
    )

@Composable
private fun Body(text: String, color: Color, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = size, fontWeight = weight, lineHeight = size * 1.35f, modifier = modifier)

/** Eyebrow at the top-left of every card. */
@Composable
internal fun WrapEyebrow(text: String, color: Color) =
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

/** One story card's words and figures over its art. */
@Composable
internal fun WrapCardContent(
    kind: WrapCardKind,
    cards: WrapCards,
    money: WrapMoney,
    hideNames: Boolean,
    periodLong: String,
    personality: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    val ink = inkOf(kind)
    val muted = ink.copy(alpha = 0.82f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val eyebrow = when (kind) {
            WrapCardKind.INTRO, WrapCardKind.SUMMARY -> "LipaBill Wrap · $periodLong"
            else -> kind.title
        }
        WrapEyebrow(eyebrow, ink)
        when (kind) {
            WrapCardKind.SUMMARY -> SummaryBody(cards, money, hideNames, personality.first)
            WrapCardKind.INTRO -> {
                Spacer(Modifier.weight(1f))
                val name = cards.periodName()
                WrapBig(if (name.endsWith("so far")) "Here's your $name." else "This was your $name with money.", ink, 40.sp)
                Body(
                    if (cards.partial) "Counting from when LipaBill's records start. Tap through to see how it went."
                    else "Tap through to see how it went.",
                    muted
                )
            }
            WrapCardKind.NUMBERS -> NumbersBody(cards, ink, muted)
            WrapCardKind.FLOW -> FlowBody(cards, money, muted)
            WrapCardKind.FEES -> {
                Spacer(Modifier.weight(1f))
                WrapBig("Transaction charges took", ink, 30.sp)
                WrapBig(money.full(cards.fees), ink, 44.sp)
                Body(
                    buildString {
                        append("across ${cards.chargedTxnCount} charged transactions.")
                        cards.feeShareOfOut?.let { append(" That's ${String.format(Locale.US, "%.1f", it * 100)}% of everything that left your accounts.") }
                    },
                    muted
                )
            }
            WrapCardKind.FULIZA -> {
                val f = cards.fuliza
                Spacer(Modifier.weight(1f))
                WrapBig("You Fuliza'd", ink, 30.sp)
                WrapBig("${f?.count ?: 0}×", ink, 56.sp)
                if (f != null) Body("borrowing ${money.full(f.total)} in total. Average top-up: ${money.full(f.average)}.", ink)
            }
            WrapCardKind.PEOPLE -> RankedBody("Most of your send money went to", cards.topPeople, "transfer", ink, ink, money, if (hideNames) "Person" else null)
            WrapCardKind.SPOTS -> RankedBody("Where you paid most", cards.topSpots, "payment", ink, WrapPalette.Accent, money, if (hideNames) "Shop" else null)
            WrapCardKind.RHYTHM -> RhythmBody(cards, money, hideNames)
            WrapCardKind.PERSONALITY -> {
                Spacer(Modifier.weight(1f))
                WrapBig(personality.first, ink, 44.sp)
                Body(personality.second, muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.SummaryBody(c: WrapCards, money: WrapMoney, hideNames: Boolean, personalityTitle: String) {
    Spacer(Modifier.height(6.dp))
    Body("Money out", Color.White.copy(alpha = 0.8f), 12.sp)
    WrapBig(money.short(c.moneyOut), WrapPalette.Accent, 30.sp)
    Body("Money in", Color.White.copy(alpha = 0.8f), 12.sp)
    WrapBig(money.short(c.moneyIn), Color.White, 26.sp)
    Spacer(Modifier.height(6.dp))
    @Composable
    fun Line(label: String, value: String, coverName: Boolean = false) {
        Column {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Body(label, Color.White.copy(alpha = 0.85f), 12.sp, modifier = Modifier.weight(1f))
                Text(
                    value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.recordingPrivacyCover(coverName)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
        }
    }
    Line("Transactions", "%,d".format(c.txnCount))
    Line("Charges paid", money.full(c.fees))
    c.fuliza?.let { Line("Fuliza", "${it.count}×") }
    c.topSpots.firstOrNull()?.let { Line("Top spot", if (hideNames) "Shop 1" else it.name, !hideNames) }
    c.topPeople.firstOrNull()?.let { Line("Top person", if (hideNames) "Person 1" else it.name, !hideNames) }
    Spacer(Modifier.weight(1f))
    Body("My money personality", Color.White.copy(alpha = 0.7f), 11.sp)
    WrapBig(personalityTitle, Color.White, 22.sp)
}

private fun typeTiles(c: WrapCards): List<Pair<String, Int>> {
    fun n(vararg t: TransactionType) = t.sumOf { c.typeCounts[it] ?: 0 }
    return listOf(
        "Tills" to n(TransactionType.BUY_GOODS, TransactionType.POCHI),
        "Charges" to c.chargedTxnCount,
        "Sent" to n(TransactionType.SENT),
        "Airtime" to n(TransactionType.AIRTIME),
        "Withdrawals" to n(TransactionType.WITHDRAW),
        "Paybills" to n(TransactionType.PAYBILL),
        "Received" to n(TransactionType.RECEIVED, TransactionType.DEPOSIT),
        "Fuliza" to n(TransactionType.FULIZA)
    ).filter { it.second > 0 }
}

@Composable
private fun ColumnScope.NumbersBody(c: WrapCards, ink: Color, muted: Color) {
    WrapBig("%,d".format(c.txnCount), ink, 50.sp)
    Body(
        "transactions. That's ${String.format(Locale.US, "%.1f", c.perDay)} a day, on ${c.activeDays} of ${c.coveredDays} days.",
        ink
    )
    Spacer(Modifier.weight(1f))
    typeTiles(c).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ink.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Body(label, muted, 13.sp, modifier = Modifier.weight(1f))
                    Text("%,d".format(count), color = ink, fontSize = 15.sp, fontFamily = WrapDisplay)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    c.busiestDay?.let { (day, count) ->
        Body("Busiest day: $count transactions on ${day.format(WrapDayMonth)}.", ink, 13.sp, FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.FlowBody(c: WrapCards, money: WrapMoney, muted: Color) {
    Spacer(Modifier.weight(1f))
    Body("Money out", Color.White, 14.sp)
    WrapBig(money.short(c.moneyOut), WrapPalette.Accent, 46.sp)
    Body("Money in", Color.White, 14.sp)
    WrapBig(money.short(c.moneyIn), Color.White, 46.sp)
    Body(
        buildString {
            c.heaviestOutDay?.let { (day, amount) -> append("Your heaviest day was ${day.format(WrapDayMonth)}: ${money.full(amount)} out.") }
            val net = c.net
            if (isNotEmpty()) append(" ")
            append(
                when {
                    net >= 1.0 -> "You kept ${money.full(net)}."
                    net <= -1.0 -> "${money.full(-net)} more went out than came in."
                    else -> "In and out came out even."
                }
            )
        },
        muted
    )
}

@Composable
private fun ColumnScope.RankedBody(
    lead: String,
    rows: List<CounterpartySpend>,
    countWord: String,
    ink: Color,
    accent: Color,
    money: WrapMoney,
    placeholder: String?
) {
    val top = rows.firstOrNull() ?: return
    fun nameOf(i: Int, row: CounterpartySpend) = if (placeholder != null) "$placeholder ${i + 1}" else row.name
    Body(lead, ink, 14.sp)
    WrapBig(nameOf(0, top), accent, 32.sp, Modifier.recordingPrivacyCover(placeholder == null))
    Body("${top.count} $countWord${if (top.count == 1) "" else "s"} · ${money.full(top.total)}", ink.copy(alpha = 0.85f), 13.sp)
    Spacer(Modifier.weight(1f))
    val max = rows.maxOf { it.total }.coerceAtLeast(1.0)
    rows.forEachIndexed { i, row ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = ink.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = WrapDisplay, modifier = Modifier.width(24.dp))
                Text(
                    nameOf(i, row), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).recordingPrivacyCover(placeholder == null)
                )
                Text(money.short(row.total), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.padding(start = 24.dp).fillMaxWidth().height(5.dp).clip(CircleShape)
                    .background(ink.copy(alpha = 0.22f))
            ) {
                Box(
                    Modifier.fillMaxWidth((row.total / max).toFloat().coerceIn(0.04f, 1f)).fillMaxHeight()
                        .clip(CircleShape).background(ink)
                )
            }
        }
    }
}

private fun weekdayPlural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.UK) + "s"

@Composable
private fun ColumnScope.RhythmBody(c: WrapCards, money: WrapMoney, hideNames: Boolean) {
    val ink = WrapPalette.Ink
    val peak = c.bars.getOrNull(c.peakBar)
    val peakName = peak?.label?.let { if (it.startsWith("W")) "Week ${it.drop(1)}" else it.substringBefore(' ') }
    WrapBig(if (peakName != null) "$peakName hit hardest." else "Your rhythm.", ink, 30.sp)
    @Composable
    fun Tile(value: String, label: String, modifier: Modifier, cover: Boolean = false) {
        Column(
            modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.55f)).padding(10.dp)
        ) {
            Text(value, color = ink, fontSize = 17.sp, fontFamily = WrapDisplay)
            Body(label, ink.copy(alpha = 0.75f), 11.sp, modifier = Modifier.recordingPrivacyCover(cover))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        c.busiestWeekday?.let { Tile(weekdayPlural(it), "busiest day", Modifier.weight(1f)) }
        if (c.afterMidnight > 0) Tile("${c.afterMidnight}", "payments after midnight", Modifier.weight(1f))
        else Tile("${c.quietDays}", "days nothing went out", Modifier.weight(1f))
    }
    c.biggestPayment?.let {
        Tile(
            money.full(it.amount),
            if (hideNames) "biggest single payment" else "biggest single payment · ${it.name}",
            Modifier.fillMaxWidth(),
            cover = !hideNames
        )
    }
    Spacer(Modifier.weight(1f))
    val max = c.bars.maxOf { it.outflow }.coerceAtLeast(1.0)
    Row(
        modifier = Modifier.fillMaxWidth().height(118.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        c.bars.forEachIndexed { i, bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth()
                        .height((96.dp * (bar.outflow / max).toFloat()).coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (i == c.peakBar) WrapPalette.Navy else WrapPalette.LabelBlue)
                )
                Text(
                    if (bar.label.startsWith("W")) bar.label else bar.label.take(1),
                    color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }
    }
}

/** Title and one-liner for the card's money personality. */
@Composable
internal fun personalityText(c: WrapCards): Pair<String, String> =
    when (c.personality) {
        WrapPersonality.FULIZA_FREQUENT ->
            stringResource(R.string.wrap_personality_fuliza_frequent) to
                stringResource(R.string.wrap_personality_fuliza_frequent_body, c.fuliza?.count ?: 0)
        WrapPersonality.FAMILY_ANCHOR ->
            stringResource(R.string.wrap_personality_family_anchor) to stringResource(R.string.wrap_personality_family_anchor_body)
        WrapPersonality.FEE_HEAVY ->
            stringResource(R.string.wrap_personality_fee_heavy) to stringResource(R.string.wrap_personality_fee_heavy_body)
        WrapPersonality.QUIET_OPERATOR ->
            stringResource(R.string.wrap_personality_quiet_operator) to stringResource(R.string.wrap_personality_quiet_operator_body)
        WrapPersonality.EVERYDAY_MOVER ->
            stringResource(R.string.wrap_personality_everyday_mover) to stringResource(R.string.wrap_personality_everyday_mover_body)
    }

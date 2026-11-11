package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CostInsightsTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun reversal_and_the_payment_it_undid_both_drop_out() {
        val paid = tx(TransactionType.SENT, "Jane", 1_500.0, LocalDate.of(2026, 11, 2), code = "QK12ABC34D")
        val reversal = tx(
            TransactionType.REVERSED,
            null,
            1_500.0,
            LocalDate.of(2026, 11, 3),
            code = "QK13XYZ99Z",
            raw = "QK13XYZ99Z Confirmed. Transaction QK12ABC34D has been reversed. " +
                "Your original transaction QK12ABC34D in favour of JANE DOE has been reversed."
        )
        val kept = tx(TransactionType.PAYBILL, "KPLC", 900.0, LocalDate.of(2026, 11, 4), code = "QK14KPL00A")

        val clean = withoutReversals(listOf(paid, reversal, kept))

        assertEquals(listOf(kept), clean)
        assertEquals("QK12ABC34D", reversedOriginalCode(reversal))
    }

    @Test
    fun reversal_without_a_matching_payment_is_not_income() {
        val reversal = tx(TransactionType.REVERSED, null, 300.0, LocalDate.of(2026, 11, 3), raw = "reversed")
        assertTrue(withoutReversals(listOf(reversal)).isEmpty())
    }

    @Test
    fun multiple_reversals_drop_each_matched_original() {
        val a = tx(TransactionType.SENT, "Jane", 100.0, LocalDate.of(2026, 11, 1), code = "QKAAA11111")
        val b = tx(TransactionType.SENT, "Bob", 200.0, LocalDate.of(2026, 11, 2), code = "QKBBB22222")
        val keep = tx(TransactionType.SENT, "Sam", 50.0, LocalDate.of(2026, 11, 3), code = "QKCCC33333")
        val revA = tx(
            TransactionType.REVERSED, null, 100.0, LocalDate.of(2026, 11, 4), code = "RK1",
            raw = "original transaction QKAAA11111 in favour of Jane has been reversed"
        )
        val revB = tx(
            TransactionType.REVERSED, null, 200.0, LocalDate.of(2026, 11, 5), code = "RK2",
            raw = "original transaction QKBBB22222 in favour of Bob has been reversed"
        )
        assertEquals(setOf("QKAAA11111", "QKBBB22222"), reversedCodes(listOf(a, b, keep, revA, revB)))
        assertEquals(listOf(keep), withoutReversals(listOf(a, b, keep, revA, revB)))
    }

    @Test
    fun fees_count_outgoing_costs_and_withdrawals_separately() {
        val fees = feeSummary(
            listOf(
                tx(TransactionType.WITHDRAW, "Agent", 1_000.0, LocalDate.of(2026, 11, 1), cost = 29.0),
                tx(TransactionType.WITHDRAW, "Agent", 2_000.0, LocalDate.of(2026, 11, 2), cost = 29.0),
                tx(TransactionType.SENT, "Jane", 500.0, LocalDate.of(2026, 11, 3), cost = 7.0),
                tx(TransactionType.RECEIVED, "Boss", 9_000.0, LocalDate.of(2026, 11, 4), cost = 50.0)
            )
        )
        assertEquals(65.0, fees.total, 0.001)
        assertEquals(2, fees.withdrawCount)
        assertEquals(58.0, fees.withdrawFees, 0.001)
    }

    @Test
    fun fee_summary_ignores_received_and_fuliza_costs() {
        val fees = feeSummary(
            listOf(
                tx(TransactionType.RECEIVED, "Boss", 9_000.0, LocalDate.of(2026, 11, 1), cost = 50.0),
                tx(TransactionType.FULIZA, "Due", 500.0, LocalDate.of(2026, 11, 2), cost = 12.0),
                tx(TransactionType.SENT, "Jane", 100.0, LocalDate.of(2026, 11, 3), cost = 7.0)
            )
        )
        assertEquals(7.0, fees.total, 0.001)
        assertEquals(0, fees.withdrawCount)
    }

    @Test
    fun monthly_payee_is_a_regular_with_next_due_date() {
        val today = LocalDate.of(2026, 11, 10)
        val fibre = listOf(
            LocalDate.of(2026, 8, 14),
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 10, 14)
        ).map { tx(TransactionType.PAYBILL, "Safaricom Home", 2_999.0, it) }

        val regulars = detectRegulars(fibre, zone, today)

        assertEquals(1, regulars.size)
        val regular = regulars.single()
        assertEquals(Cadence.MONTHLY, regular.cadence)
        assertEquals(2_999.0, regular.typicalAmount, 0.001)
        assertEquals(LocalDate.of(2026, 11, 14), regular.nextDue)
        assertEquals(3, regular.payments)
    }

    @Test
    fun two_similar_monthly_pays_still_count_on_short_history() {
        val today = LocalDate.of(2026, 11, 10)
        val fibre = listOf(
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 10, 14)
        ).map { tx(TransactionType.PAYBILL, "Safaricom Home", 2_999.0, it) }

        val regular = detectRegulars(fibre, zone, today).single()
        assertEquals(Cadence.MONTHLY, regular.cadence)
        assertEquals(2, regular.payments)
        // Sep 14 → Oct 14 is 30 days, so next due is Oct 14 + 30.
        assertEquals(LocalDate.of(2026, 11, 13), regular.nextDue)
    }

    @Test
    fun late_rent_around_forty_days_is_still_monthly() {
        val today = LocalDate.of(2026, 11, 10)
        val rent = listOf(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 9, 20)
        ).map { tx(TransactionType.SENT, "Landlord", 15_000.0, it) }

        val regular = detectRegulars(rent, zone, today).single()
        assertEquals(Cadence.MONTHLY, regular.cadence)
    }

    @Test
    fun named_and_phone_only_sends_collapse_to_one_key_but_people_are_not_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val phone = "254712345678"
        val pays = listOf(
            tx(TransactionType.SENT, "JOHN KAMAU", 500.0, LocalDate.of(2026, 10, 20), phone = phone),
            tx(TransactionType.SENT, null, 500.0, LocalDate.of(2026, 10, 27), phone = "0712345678"),
            tx(TransactionType.SENT, "John Kamau", 520.0, LocalDate.of(2026, 11, 3), phone = phone)
        )
        assertEquals("tel:712345678", payeeKey(pays.first()))
        // Coming up is for bills and subscriptions, not family and friends.
        assertTrue(detectRegulars(pays, zone, today).isEmpty())
        assertTrue(!isBusinessLikeRegular("JOHN KAMAU", "tel:712345678", TransactionType.SENT))
    }

    @Test
    fun monthly_subscription_and_paybill_count_as_business_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val dstv = listOf(
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 10, 5)
        ).map { tx(TransactionType.PAYBILL, "DSTV Kenya", 2_450.0, it) }
        val person = listOf(
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 10, 5)
        ).map { tx(TransactionType.SENT, "Mary Wanjiku", 2_000.0, it, phone = "254711000111") }

        val regulars = detectRegulars(dstv + person, zone, today)
        assertEquals(1, regulars.size)
        assertEquals("DSTV Kenya", regulars.single().name)
        assertEquals(Cadence.MONTHLY, regulars.single().cadence)
        assertTrue(isBusinessLikeRegular("Safaricom Limited", "safaricom limited", TransactionType.SENT))
        assertTrue(isBusinessLikeRegular("Landlord", "landlord", TransactionType.SENT))
        assertTrue(!isBusinessLikeRegular("Mary Wanjiku", "tel:711000111", TransactionType.SENT))
        assertTrue(isBusinessPayee(dstv.first()))
        assertTrue(!isBusinessPayee(person.first()))
    }

    @Test
    fun till_branches_fold_into_one_habit() {
        val day = LocalDate.of(2026, 11, 5)
        val txs = listOf(
            tx(TransactionType.BUY_GOODS, "Java House Westlands", 450.0, day),
            tx(TransactionType.BUY_GOODS, "Java House Kilimani", 480.0, day.plusDays(1)),
            tx(TransactionType.BUY_GOODS, "Artcaffe", 2_000.0, day)
        )
        assertEquals("java house", foldPayeeName("Java House Westlands"))
        assertEquals("java house", foldPayeeName("Java House Kilimani"))
        val habit = topHabit(txs, zone, minVisits = 2)
        assertTrue(habit?.name?.startsWith("Java House") == true)
        assertEquals(2, habit?.visits)
    }

    @Test
    fun paybill_placeholder_folds_to_bill_id_while_sms_names_stay_on_name() {
        // Short business numbers are not Kenyan mobiles, so phone-first identity does not
        // collapse "Paybill 888880" with "Safaricom Limited" — that still needs merchant learning.
        assertEquals(
            "bill:888880",
            payeeKey(tx(TransactionType.PAYBILL, "Paybill 888880", 100.0, LocalDate.of(2026, 11, 1)))
        )
        assertEquals(
            "safaricom limited",
            payeeKey(
                tx(
                    TransactionType.PAYBILL,
                    "Safaricom Limited",
                    100.0,
                    LocalDate.of(2026, 11, 1),
                    phone = "888880"
                )
            )
        )
        assertNull(phoneKey("888880"))
    }

    @Test
    fun regular_keeps_named_display_when_latest_sms_is_phone_only() {
        val today = LocalDate.of(2026, 11, 10)
        val phone = "254700111222"
        val pays = listOf(
            tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, 20), phone = phone),
            tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, 27), phone = phone),
            tx(TransactionType.SENT, null, 5_000.0, LocalDate.of(2026, 11, 3), phone = phone)
        )
        val regular = detectRegulars(pays, zone, today, essentials = setOf("landlord")).single()
        assertEquals("Landlord", regular.name)
        assertTrue(regular.essential)
    }

    @Test
    fun irregular_or_stopped_payees_are_not_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val scattered = listOf(1, 3, 20, 21).map {
            tx(TransactionType.SENT, "Kevin", 400.0, LocalDate.of(2026, 10, it))
        }
        val stopped = listOf(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 5, 1)
        ).map { tx(TransactionType.PAYBILL, "Old Gym", 3_000.0, it) }

        assertTrue(detectRegulars(scattered + stopped, zone, today).isEmpty())
    }

    @Test
    fun essential_payees_are_flagged() {
        val today = LocalDate.of(2026, 11, 10)
        val rent = listOf(5, 12, 19, 26).map {
            tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, it))
        }
        val regular = detectRegulars(rent, zone, today, essentials = setOf("landlord")).single()
        assertEquals(Cadence.WEEKLY, regular.cadence)
        assertTrue(regular.essential)
    }

    @Test
    fun essential_name_mark_still_matches_phone_keyed_regular() {
        val today = LocalDate.of(2026, 11, 10)
        val rent = listOf(5, 12, 19, 26).map {
            tx(
                TransactionType.SENT,
                "Landlord",
                5_000.0,
                LocalDate.of(2026, 10, it),
                phone = "254700111222"
            )
        }
        val regular = detectRegulars(rent, zone, today, essentials = setOf("landlord")).single()
        assertEquals("tel:700111222", regular.key)
        assertTrue(regular.essential)
    }

    @Test
    fun top_habit_counts_separate_days_at_shops_outside_regulars() {
        val days = (2..5).map { LocalDate.of(2026, 11, it) }
        val txs = days.map { tx(TransactionType.BUY_GOODS, "Java House", 450.0, it) } +
            // Two payments on one day are one visit.
            tx(TransactionType.BUY_GOODS, "Java House", 200.0, days.last()) +
            List(5) { tx(TransactionType.PAYBILL, "Safaricom Home", 100.0, LocalDate.of(2026, 11, it + 1)) }

        val habit = topHabit(txs, zone, exclude = setOf("safaricom home"))

        assertEquals("Java House", habit?.name)
        assertEquals(4, habit?.visits)
        assertEquals(2_000.0, habit?.total ?: 0.0, 0.001)
    }

    @Test
    fun people_groceries_and_power_are_never_habits() {
        val days = (1..6).map { LocalDate.of(2026, 11, it) }
        val txs = days.map { tx(TransactionType.SENT, "Mum", 1_000.0, it) } +
            days.map { tx(TransactionType.BUY_GOODS, "NAIVAS WESTLANDS", 800.0, it) } +
            days.map { tx(TransactionType.PAYBILL, "KPLC PREPAID", 500.0, it) } +
            days.map { tx(TransactionType.BUY_GOODS, "Quickmart Kilimani", 600.0, it) }
        assertNull(topHabit(txs, zone))
    }

    @Test
    fun a_shop_with_changing_baskets_is_not_a_regular() {
        val today = LocalDate.of(2026, 11, 10)
        val amounts = listOf(350.0, 2_400.0, 900.0, 4_100.0, 600.0)
        val shop = amounts.mapIndexed { i, amount ->
            tx(TransactionType.BUY_GOODS, "Corner Shop", amount, LocalDate.of(2026, 10, 6).plusWeeks(i.toLong()))
        }
        assertTrue(detectRegulars(shop, zone, today).isEmpty())
    }

    @Test
    fun a_bill_split_across_two_payments_on_one_day_is_one_payment() {
        val today = LocalDate.of(2026, 11, 10)
        val rent = listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))
            .flatMap {
                listOf(
                    tx(TransactionType.PAYBILL, "Landlord", 10_000.0, it),
                    tx(TransactionType.PAYBILL, "Landlord", 5_000.0, it)
                )
            }
        val regular = detectRegulars(rent, zone, today).single()
        assertEquals(15_000.0, regular.typicalAmount, 0.001)
        assertEquals(3, regular.payments)
    }

    @Test
    fun savings_moves_and_fuliza_draws_are_own_money() {
        val day = LocalDate.of(2026, 11, 3)
        assertTrue(isOwnMoneyMove(tx(TransactionType.SAVINGS, "M-Shwari", 1_000.0, day)))
        assertTrue(isOwnMoneyMove(tx(TransactionType.DEPOSIT, "M-Shwari", 1_000.0, day)))
        assertTrue(isOwnMoneyMove(tx(TransactionType.FULIZA, null, 300.0, day)))
        assertTrue(!isOwnMoneyMove(tx(TransactionType.DEPOSIT, "Agent 123", 1_000.0, day)))
        assertTrue(!isOwnMoneyMove(tx(TransactionType.RECEIVED, "Boss", 9_000.0, day)))
    }

    @Test
    fun short_ranges_need_fewer_habit_visits() {
        assertEquals(2, habitMinVisits(7))
        assertEquals(2, habitMinVisits(14))
        assertEquals(3, habitMinVisits(30))
        val day = LocalDate.of(2026, 11, 5)
        val txs = List(2) { tx(TransactionType.BUY_GOODS, "Java House", 1_000.0, day.plusDays(it.toLong())) }
        assertEquals("Java House", topHabit(txs, zone, minVisits = habitMinVisits(7))?.name)
        assertNull(topHabit(txs, zone, minVisits = habitMinVisits(30)))
    }

    @Test
    fun airtime_is_not_treated_as_a_regular_bill() {
        val today = LocalDate.of(2026, 11, 10)
        val airtime = listOf(5, 12, 19, 26).map {
            tx(TransactionType.AIRTIME, "Airtime", 100.0, LocalDate.of(2026, 10, it))
        }
        assertTrue(detectRegulars(airtime, zone, today).isEmpty())
    }

    @Test
    fun kept_headline_compares_net_with_the_prior_stretch() {
        assertEquals(
            "You kept KSh 4,820 more than the stretch before.",
            keptHeadline(net = 5_820.0, previousNet = 1_000.0, hasPrior = true)
        )
        assertEquals(
            "You kept KSh 200 less than the stretch before.",
            keptHeadline(net = 800.0, previousNet = 1_000.0, hasPrior = true)
        )
        assertNull(keptHeadline(net = 800.0, previousNet = 0.0, hasPrior = false))
    }

    @Test
    fun due_regulars_mark_pulse_cells_with_what_is_due() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val fibre = regular("Safaricom Home", 2_999.0, LocalDate.of(2026, 11, 14))
        val cells = pulseCalendar(
            from = from,
            to = to,
            today = LocalDate.of(2026, 11, 10),
            inflowByDate = emptyMap(),
            outflowByDate = emptyMap(),
            regulars = listOf(fibre)
        )
        val due = cells.filter { it.isDue }
        assertEquals(listOf(LocalDate.of(2026, 11, 14)), due.map { it.date })
        assertEquals(listOf("Safaricom Home"), due.single().due.map { it.name })
    }

    @Test
    fun upcoming_due_lists_overdue_and_the_next_two_weeks_soonest_first() {
        val today = LocalDate.of(2026, 11, 10)
        val late = regular("Landlord", 15_000.0, LocalDate.of(2026, 11, 8))
        val soon = regular("Safaricom Home", 2_999.0, LocalDate.of(2026, 11, 14))
        val far = regular("School", 30_000.0, LocalDate.of(2026, 12, 20))
        assertEquals(
            listOf("Landlord", "Safaricom Home"),
            upcomingDue(listOf(far, soon, late), today).map { it.name }
        )
        assertEquals("Was due 8 Nov, not paid yet", dueCopy(late.nextDue, today))
        assertEquals("Due tomorrow", dueCopy(today.plusDays(1), today))
        assertEquals("Due Sat 14 Nov", dueCopy(soon.nextDue, today))
    }

    private fun regular(name: String, amount: Double, due: LocalDate) = RegularPayee(
        key = name.lowercase(),
        name = name,
        cadence = Cadence.MONTHLY,
        typicalAmount = amount,
        monthlyCost = amount,
        lastPaid = due.minusMonths(1),
        nextDue = due,
        payments = 3
    )

    @Test
    fun badges_track_fee_cuts_quiet_runs_and_reviewed_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val regulars = detectRegulars(
            listOf(5, 12, 19, 26).map { tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, it)) },
            zone,
            today,
            essentials = setOf("landlord")
        )
        val badges = insightBadges(
            fees = 80.0,
            previousFees = 120.0,
            hasPrior = true,
            quietStreak = 12,
            regulars = regulars,
            earned = emptySet()
        ).associateBy { it.id }

        assertTrue(badges.getValue(BADGE_FEE_CUT).earned)
        assertEquals("12/30", badges.getValue(BADGE_QUIET_30).mark)
        assertTrue(badges.getValue(BADGE_REGULARS_REVIEWED).earned)
    }

    @Test
    fun earned_badges_stay_earned() {
        val badges = insightBadges(
            fees = 500.0,
            previousFees = 100.0,
            hasPrior = true,
            quietStreak = 0,
            regulars = emptyList(),
            earned = setOf(BADGE_QUIET_30)
        ).associateBy { it.id }
        assertTrue(badges.getValue(BADGE_QUIET_30).earned)
        assertEquals(false, badges.getValue(BADGE_FEE_CUT).earned)
    }

    @Test
    fun long_stretches_get_month_bars_and_short_ones_none() {
        val out = mapOf(
            LocalDate.of(2026, 9, 3) to 1_000.0,
            LocalDate.of(2026, 10, 3) to 2_000.0,
            LocalDate.of(2026, 10, 20) to 500.0
        )
        val bars = periodBars(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 10), emptyMap(), out)
        assertEquals(listOf("Sep", "Oct", "Nov"), bars.map { it.label })
        assertEquals(2_500.0, bars[1].outflow, 0.001)
        assertTrue(periodBars(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), emptyMap(), out).isEmpty())
    }

    @Test
    fun average_daily_spend_looks_back_four_weeks_before_the_start() {
        val start = LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val txs = listOf(
            tx(TransactionType.BUY_GOODS, "Java House", 560.0, LocalDate.of(2026, 10, 20)),
            tx(TransactionType.BUY_GOODS, "Java House", 560.0, LocalDate.of(2026, 10, 27)),
            tx(TransactionType.BUY_GOODS, "Java House", 999.0, LocalDate.of(2026, 11, 2)),
            tx(TransactionType.BUY_GOODS, "Naivas", 5_000.0, LocalDate.of(2026, 10, 25))
        )
        val daily = averageDailySpend(txs, beforeMillis = start) { it.counterpartyName == "Java House" }
        assertEquals(40.0, daily, 0.001)
    }

    @Test
    fun business_payee_edges_brands_digits_long_names_and_airtime() {
        assertTrue(isBusinessLikeRegular("Netflix", "netflix", TransactionType.SENT))
        assertTrue(isBusinessLikeRegular("Shop 123", "shop 123", TransactionType.SENT))
        assertTrue(isBusinessLikeRegular("Kenya Power", "kenya power", TransactionType.POCHI))
        assertTrue(
            !isBusinessLikeRegular(
                "A B C D E",
                "a b c d e",
                TransactionType.SENT
            )
        )
        assertTrue(isBusinessPayee(tx(TransactionType.AIRTIME, "Airtime", 50.0, LocalDate.of(2026, 11, 1))))
        assertTrue(isBusinessPayee(tx(TransactionType.BUY_GOODS, null, 100.0, LocalDate.of(2026, 11, 1), phone = "0700111222")))
        assertTrue(isOwnMoneyMove(tx(TransactionType.SAVINGS, "M-Shwari", 100.0, LocalDate.of(2026, 11, 1))))
        assertTrue(isOwnMoneyMove(tx(TransactionType.DEPOSIT, "M-Shwari", 100.0, LocalDate.of(2026, 11, 1))))
        assertTrue(!isOwnMoneyMove(tx(TransactionType.DEPOSIT, "Agent", 100.0, LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun cadence_copy_covers_overdue_today_and_upcoming() {
        val today = LocalDate.of(2026, 11, 11)
        fun regular(due: LocalDate) = RegularPayee(
            key = "bill:1",
            name = "Fibre",
            cadence = Cadence.MONTHLY,
            typicalAmount = 2_999.0,
            monthlyCost = 2_999.0,
            lastPaid = due.minusDays(30),
            nextDue = due,
            payments = 3
        )
        assertTrue(regularCadenceCopy(regular(today.minusDays(2)), today).contains("was due"))
        assertTrue(regularCadenceCopy(regular(today), today).contains("due today"))
        assertTrue(regularCadenceCopy(regular(today.plusDays(5)), today).startsWith("Monthly · next"))
    }

    @Test
    fun payee_mark_candidates_include_name_and_phone_aliases() {
        val tx = tx(
            TransactionType.SENT,
            "JOHN KAMAU",
            500.0,
            LocalDate.of(2026, 11, 1),
            phone = "254712345678"
        )
        val marks = payeeMarkCandidates(tx)
        assertTrue(marks.contains("tel:712345678"))
        assertTrue(marks.contains("john kamau"))
        assertTrue(marks.contains("tel:254712345678") || marks.any { it.startsWith("tel:") })
    }

    @Test
    fun day_flow_tells_one_way_days_from_net_up_and_net_down() {
        val day = LocalDate.of(2026, 11, 5)
        fun cell(inflow: Double, outflow: Double) = PulseCell(day, true, false, inflow, outflow)
        assertEquals(DayFlow.IN_ONLY, cell(1_000.0, 0.0).flow)
        assertEquals(DayFlow.OUT_ONLY, cell(0.0, 400.0).flow)
        assertEquals(DayFlow.NET_UP, cell(1_000.0, 400.0).flow)
        assertEquals(DayFlow.NET_DOWN, cell(300.0, 400.0).flow)
        assertEquals(DayFlow.NONE, cell(0.0, 0.0).flow)
    }

    private fun tx(
        type: TransactionType,
        name: String?,
        amount: Double,
        date: LocalDate,
        code: String = "A${date}${name.orEmpty()}",
        cost: Double? = null,
        raw: String = "",
        phone: String? = null
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        balance = null,
        cost = cost,
        rawBody = raw
    )
}

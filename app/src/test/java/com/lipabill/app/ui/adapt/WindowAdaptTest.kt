package com.lipabill.app.ui.adapt

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowAdaptTest {

    @Test
    fun width_classes_match_phone_foldable_and_tablet() {
        assertEquals(WindowWidth.Compact, windowFormFor(360).width)
        assertEquals(WindowWidth.Compact, windowFormFor(599).width)
        assertEquals(WindowWidth.Medium, windowFormFor(600).width)
        assertEquals(WindowWidth.Medium, windowFormFor(673).width)
        assertEquals(WindowWidth.Medium, windowFormFor(839).width)
        assertEquals(WindowWidth.Expanded, windowFormFor(840).width)
        assertEquals(WindowWidth.Expanded, windowFormFor(1280).width)
    }

    @Test
    fun height_classes_match_short_phone_and_tall_tablet() {
        assertEquals(WindowHeight.Compact, windowFormFor(360, 600).height)
        assertEquals(WindowHeight.Compact, windowFormFor(360, 639).height)
        assertEquals(WindowHeight.Medium, windowFormFor(360, 640).height)
        assertEquals(WindowHeight.Medium, windowFormFor(360, 800).height)
        assertEquals(WindowHeight.Expanded, windowFormFor(840, 840).height)
        assertEquals(WindowHeight.Expanded, windowFormFor(1280, 1024).height)
    }

    @Test
    fun money_sheet_uses_more_height_on_short_windows() {
        assertEquals(0.94f, moneySheetHeightFraction(600), 0.001f)
        assertEquals(0.90f, moneySheetHeightFraction(720), 0.001f)
        assertEquals(0.88f, moneySheetHeightFraction(1000), 0.001f)
    }
}

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
}

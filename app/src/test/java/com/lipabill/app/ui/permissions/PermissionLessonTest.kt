package com.lipabill.app.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionLessonTest {

    @Test
    fun nothing_missing_skips_the_lesson() {
        assertNull(phoneLessonFor(needCall = false, needState = false))
    }

    @Test
    fun call_only() {
        assertEquals(PermissionLesson.Phone, phoneLessonFor(needCall = true, needState = false))
    }

    @Test
    fun sim_only() {
        assertEquals(
            PermissionLesson.PhoneState,
            phoneLessonFor(needCall = false, needState = true)
        )
    }

    @Test
    fun call_and_sim_together_when_both_block_the_same_step() {
        assertEquals(
            PermissionLesson.PhoneAndSim,
            phoneLessonFor(needCall = true, needState = true)
        )
    }
}

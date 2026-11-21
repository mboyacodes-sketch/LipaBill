package com.lipabill.app.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedAppRestrictionsTest {

    @Test
    fun enabled_platform_statuses_ask_once() {
        listOf(3, 4, 5).forEach { status ->
            assertEquals(UnusedAppRestriction.Enabled, unusedAppRestrictionOf(status))
            assertTrue(
                shouldAskToDisableHibernation(
                    unusedAppRestrictionOf(status),
                    alreadyAsked = false
                )
            )
        }
    }

    @Test
    fun disabled_unavailable_and_unknown_do_not_ask() {
        assertEquals(UnusedAppRestriction.Unavailable, unusedAppRestrictionOf(1))
        assertEquals(UnusedAppRestriction.Disabled, unusedAppRestrictionOf(2))
        assertEquals(UnusedAppRestriction.Unknown, unusedAppRestrictionOf(0))
        listOf(0, 1, 2).forEach { status ->
            assertFalse(
                shouldAskToDisableHibernation(
                    unusedAppRestrictionOf(status),
                    alreadyAsked = false
                )
            )
        }
    }

    @Test
    fun a_previous_answer_is_not_asked_again_without_grant_clear() {
        assertFalse(
            shouldAskToDisableHibernation(
                UnusedAppRestriction.Enabled,
                alreadyAsked = true
            )
        )
    }

    @Test
    fun grant_cleared_allows_rare_reask_after_cooldown() {
        assertTrue(
            shouldAskToDisableHibernation(
                status = UnusedAppRestriction.Enabled,
                alreadyAsked = true,
                grantClearedWhilePreferred = true,
                cooldownElapsed = true
            )
        )
        assertFalse(
            shouldAskToDisableHibernation(
                status = UnusedAppRestriction.Enabled,
                alreadyAsked = true,
                grantClearedWhilePreferred = true,
                cooldownElapsed = false
            )
        )
    }

    @Test
    fun exemption_satisfied_never_asks() {
        assertFalse(
            shouldAskToDisableHibernation(
                status = UnusedAppRestriction.Enabled,
                alreadyAsked = false,
                exemptionSatisfied = true
            )
        )
    }
}

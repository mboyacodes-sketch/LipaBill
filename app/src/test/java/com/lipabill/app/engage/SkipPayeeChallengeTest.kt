package com.lipabill.app.engage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipPayeeChallengeTest {

    @Test
    fun skip_payee_template_round_trips_through_its_id() {
        val template = ChallengeTemplates.skipPayee("Java House")
        assertEquals(7, template.durationDays)
        assertEquals(template, ChallengeTemplates.byId(template.id))
        assertEquals("Java House", ChallengeTemplates.payeeOf(template.id))
        assertNull(ChallengeTemplates.payeeOf(ChallengeTemplates.foodDelivery3Days.id))
    }

    @Test
    fun payee_rule_matches_that_payee_only() {
        val rule = CategoryRules.byId(ChallengeTemplates.skipPayee("Java House").categoryId)!!
        assertTrue(CategoryMatcher.matches(rule, "JAVA HOUSE THIKA RD"))
        assertFalse(CategoryMatcher.matches(rule, "Naivas"))
    }

    @Test
    fun analytics_never_see_the_payee_name() {
        val id = ChallengeTemplates.skipPayee("Java House").id
        assertEquals("skip_payee_7d", ChallengeTemplates.metricId(id))
        assertEquals("food_delivery_3d", ChallengeTemplates.metricId("food_delivery_3d"))
    }
}

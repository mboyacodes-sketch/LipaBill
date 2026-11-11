package com.lipabill.app.viewmodel

import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeSubjectTest {

    @Test
    fun template_ids_map_to_subjects() {
        assertEquals(ChallengeSubject.FULIZA, challengeSubject(ChallengeTemplates.fulizaFree7Days.id))
        assertEquals(ChallengeSubject.FULIZA, challengeSubject("fuliza_until_payday:3"))
        assertEquals(ChallengeSubject.FEE, challengeSubject(ChallengeTemplates.zeroFee7Days.id))
        assertEquals(ChallengeSubject.QUIET, challengeSubject(ChallengeTemplates.quietDays7.id))
        assertEquals(ChallengeSubject.PAYDAY, challengeSubject(ChallengeTemplates.paydayPause2Days.id))
        assertEquals(ChallengeSubject.FOOD_DELIVERY, challengeSubject(ChallengeTemplates.foodDelivery3Days.id))
        assertEquals(ChallengeSubject.PAYEE, challengeSubject(ChallengeTemplates.skipPayee("Java House").id))
        assertEquals(ChallengeSubject.DIY, challengeSubject("diy:food_delivery:7:50000"))
        assertEquals(ChallengeSubject.OTHER, challengeSubject("unknown_template"))
    }

    @Test
    fun card_kind_maps_status_including_cancelled_as_offer() {
        assertEquals(ChallengeCardKind.ACTIVE, challengeCardKind(ChallengeStatus.ACTIVE))
        assertEquals(ChallengeCardKind.COMPLETED, challengeCardKind(ChallengeStatus.COMPLETED))
        assertEquals(ChallengeCardKind.BROKEN, challengeCardKind(ChallengeStatus.BROKEN))
        assertEquals(ChallengeCardKind.OFFER, challengeCardKind(ChallengeStatus.CANCELLED))
    }
}

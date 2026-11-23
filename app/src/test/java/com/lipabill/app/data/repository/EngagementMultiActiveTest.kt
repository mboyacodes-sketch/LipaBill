package com.lipabill.app.data.repository

import androidx.room.Room
import com.lipabill.app.data.local.AppDatabase
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EngagementMultiActiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EngagementRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = EngagementRepository(db.challengeDao(), db.transactionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun starts_up_to_three_different_templates() = runBlocking {
        val a = repo.startChallenge(ChallengeTemplates.foodDelivery3Days.id)
        val b = repo.startChallenge(ChallengeTemplates.fulizaFree7Days.id)
        val c = repo.startChallenge(ChallengeTemplates.quietDays7.id)
        assertNotNull(a)
        assertNotNull(b)
        assertNotNull(c)
        assertEquals(3, repo.listActive().size)
        assertTrue(repo.listActive().all { it.status == ChallengeStatus.ACTIVE.name })
    }

    @Test
    fun fourth_start_blocked_at_cap() = runBlocking {
        repo.startChallenge(ChallengeTemplates.foodDelivery3Days.id)
        repo.startChallenge(ChallengeTemplates.fulizaFree7Days.id)
        repo.startChallenge(ChallengeTemplates.quietDays7.id)
        val blocked = repo.startChallenge(ChallengeTemplates.zeroFee7Days.id)
        assertNull(blocked)
        assertEquals(3, repo.listActive().size)
    }

    @Test
    fun same_template_returns_existing_without_duplicate() = runBlocking {
        val first = repo.startChallenge(ChallengeTemplates.foodDelivery3Days.id)
        val again = repo.startChallenge(ChallengeTemplates.foodDelivery3Days.id)
        assertEquals(first, again)
        assertEquals(1, repo.listActive().size)
    }

    @Test
    fun cancel_one_frees_a_slot() = runBlocking {
        val first = repo.startChallenge(ChallengeTemplates.foodDelivery3Days.id)!!
        repo.startChallenge(ChallengeTemplates.fulizaFree7Days.id)
        repo.startChallenge(ChallengeTemplates.quietDays7.id)
        repo.cancelChallenge(first)
        assertEquals(2, repo.listActive().size)
        val next = repo.startChallenge(ChallengeTemplates.zeroFee7Days.id)
        assertNotNull(next)
        assertEquals(3, repo.listActive().size)
    }
}

package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lipabill.app.data.local.entity.ChallengeAchievementEntity
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.data.local.entity.CheckInStreakEntity
import com.lipabill.app.data.local.entity.WeeklyCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenges WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<ChallengeEntity?>

    @Query(
        """
        SELECT * FROM challenges
        WHERE status = 'ACTIVE'
           OR (status = 'COMPLETED' AND :now < endAt + :graceMillis)
        """
    )
    suspend fun listEvaluable(now: Long, graceMillis: Long): List<ChallengeEntity>

    @Insert
    suspend fun insertChallenge(entity: ChallengeEntity): Long

    @Query(
        """
        UPDATE challenges
        SET status = :status,
            breakingTransactionId = :breakingTransactionId,
            completedAt = :completedAt,
            brokenAt = :brokenAt
        WHERE id = :id
        """
    )
    suspend fun updateOutcome(
        id: Long,
        status: String,
        breakingTransactionId: Long?,
        completedAt: Long?,
        brokenAt: Long?
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(entity: ChallengeAchievementEntity): Long

    @Query("SELECT * FROM weekly_checkins WHERE weekId = :weekId LIMIT 1")
    fun observeCheckIn(weekId: String): Flow<WeeklyCheckInEntity?>

    @Query("SELECT * FROM weekly_checkins WHERE weekId = :weekId LIMIT 1")
    suspend fun checkIn(weekId: String): WeeklyCheckInEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(entity: WeeklyCheckInEntity): Long

    @Query("SELECT * FROM checkin_streak WHERE id = 1 LIMIT 1")
    fun observeStreak(): Flow<CheckInStreakEntity?>

    @Query("SELECT * FROM checkin_streak WHERE id = 1 LIMIT 1")
    suspend fun streak(): CheckInStreakEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(entity: CheckInStreakEntity)
}

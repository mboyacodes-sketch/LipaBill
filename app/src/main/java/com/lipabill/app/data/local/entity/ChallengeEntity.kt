package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val startAt: Long,
    val endAt: Long,
    /** Zone captured when the challenge started. Later device zone changes do not move the window. */
    val zoneId: String,
    val status: String,
    val breakingTransactionId: Long? = null,
    val completedAt: Long? = null,
    val brokenAt: Long? = null
)

@Entity(tableName = "challenge_achievements")
data class ChallengeAchievementEntity(
    @PrimaryKey val id: String,
    val challengeId: Long,
    val grantedAt: Long
)

@Entity(tableName = "weekly_checkins")
data class WeeklyCheckInEntity(
    @PrimaryKey val weekId: String,
    val answers: String,
    val completedAt: Long
)

@Entity(tableName = "checkin_streak")
data class CheckInStreakEntity(
    @PrimaryKey val id: Int = 1,
    val count: Int = 0,
    val freezeUsed: Boolean = false,
    val lastWeekId: String? = null
)

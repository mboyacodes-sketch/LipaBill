package com.lipabill.app.ui.permissions

/**
 * Whether LipaBill should explain Android's unused-app hibernation.
 * Enabled statuses match [androidx.core.content.UnusedAppRestrictionsConstants]:
 * API_30_BACKPORT = 3, API_30 = 4, and API_31 = 5.
 */
enum class UnusedAppRestriction {
    Unknown,
    Unavailable,
    Disabled,
    Enabled
}

fun unusedAppRestrictionOf(platformStatus: Int): UnusedAppRestriction = when (platformStatus) {
    FEATURE_NOT_AVAILABLE -> UnusedAppRestriction.Unavailable
    DISABLED -> UnusedAppRestriction.Disabled
    API_30_BACKPORT, API_30, API_31 -> UnusedAppRestriction.Enabled
    else -> UnusedAppRestriction.Unknown
}

/**
 * Ask when hibernation is still on.
 *
 * [alreadyAsked] alone no longer blocks forever — when Accessibility was preferred
 * but the Secure grant cleared, allow a rare re-ask ([grantClearedWhilePreferred]).
 * [cooldownElapsed] gates how soon that re-ask may fire (14 days).
 */
fun shouldAskToDisableHibernation(
    status: UnusedAppRestriction,
    alreadyAsked: Boolean,
    grantClearedWhilePreferred: Boolean = false,
    cooldownElapsed: Boolean = true,
    exemptionSatisfied: Boolean = false
): Boolean {
    if (exemptionSatisfied) return false
    if (status != UnusedAppRestriction.Enabled) return false
    if (!alreadyAsked) return true
    return grantClearedWhilePreferred && cooldownElapsed
}

private const val FEATURE_NOT_AVAILABLE = 1
private const val DISABLED = 2
private const val API_30_BACKPORT = 3
private const val API_30 = 4
private const val API_31 = 5

/** Same window as [OemSurvivalHelper.PROMPT_COOLDOWN_MS]. */
const val HIBERNATION_REASK_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000

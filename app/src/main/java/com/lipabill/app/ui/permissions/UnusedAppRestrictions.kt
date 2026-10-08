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

fun shouldAskToDisableHibernation(
    status: UnusedAppRestriction,
    alreadyAsked: Boolean
): Boolean = status == UnusedAppRestriction.Enabled && !alreadyAsked

private const val FEATURE_NOT_AVAILABLE = 1
private const val DISABLED = 2
private const val API_30_BACKPORT = 3
private const val API_30 = 4
private const val API_31 = 5

package com.lipabill.app.engage

import kotlin.math.roundToLong

/** Whole cents. The ledger stores KES as Double; comparisons in this package use cents. */
fun kesToCents(amount: Double?): Long? {
    if (amount == null || amount.isNaN() || amount.isInfinite()) return null
    return (amount * 100.0).roundToLong()
}

fun centsToKes(cents: Long): Double = cents / 100.0

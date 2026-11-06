package com.lipabill.app.metrics

import androidx.work.ListenableWorker

/**
 * Shared ok / error / retry envelope for engagement and pass-reminder workers.
 */
suspend fun runEngagementWork(
    kind: String,
    block: suspend () -> Unit
): ListenableWorker.Result = try {
    block()
    AppMetrics.engagementWorker(kind, "ok")
    ListenableWorker.Result.success()
} catch (t: Throwable) {
    AppMetrics.engagementWorker(kind, "error")
    AppCrashReporting.recordCaught(t)
    ListenableWorker.Result.retry()
}

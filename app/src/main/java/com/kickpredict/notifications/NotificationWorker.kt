package com.kickpredict.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kickpredict.KickPredictApplication

/**
 * Periodic background scan: refreshes the current fixtures + results, then posts a notification for
 * each newly-qualifying event (strong-pick kickoff, live start, settled result) that hasn't fired yet.
 */
class NotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KickPredictApplication).container
        if (!container.notificationPreference.enabled.value) return Result.success()

        return runCatching {
            // Pull fresh fixtures/odds/live scores, then seed the latest results.
            val matches = container.getPredictedMatches(forceRefresh = true)
            runCatching { container.syncResults() }
            runCatching { container.recalibrate() }

            val pending = container.getPendingNotifications(matches, System.currentTimeMillis())
            val fresh = pending.filterNot { container.notificationLog.wasNotified(it.key) }
            if (fresh.isNotEmpty()) {
                container.matchNotifier.ensureChannel()
                fresh.forEach { container.matchNotifier.post(it) }
                container.notificationLog.markNotified(fresh.map { it.key })
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

package org.roto.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            RotoTodayWidget.refreshAll(applicationContext)
        }.fold(
            onSuccess = {
                WidgetRefreshScheduler.scheduleDailyRefresh(applicationContext)
                Result.success()
            },
            onFailure = {
                // Retry once the worker is rescheduled by WorkManager.
                WidgetRefreshScheduler.scheduleDailyRefresh(applicationContext)
                Result.retry()
            }
        )
    }
}

object WidgetRefreshScheduler {
    private const val WORK_NAME = "widget-midnight-refresh"

    fun scheduleDailyRefresh(context: Context) {
        val delayMillis = calculateDelayUntilNextMidnight()
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun calculateDelayUntilNextMidnight(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .plusMinutes(1) // small buffer past midnight
        var duration = Duration.between(now, nextMidnight)
        if (duration.isNegative || duration.isZero) {
            duration = Duration.ofMinutes(1)
        }
        return duration.toMillis()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

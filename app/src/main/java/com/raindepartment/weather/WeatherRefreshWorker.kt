package com.raindepartment.weather

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

internal fun refreshCadenceMinutes(
    forecast: DashboardForecast,
    nowEpochMillis: Long,
): Long {
    val minutesUntilRain = forecast.rainStartMinutesFromNow(nowEpochMillis)
    return when {
        forecast.isCurrentlyRaining(nowEpochMillis) -> 6L
        minutesUntilRain == null || minutesUntilRain > 180L -> 45L
        minutesUntilRain > 60L -> 12L
        else -> 6L
    }
}

internal object WeatherRefreshScheduler {
    internal const val WORK_NAME = "weather_refresh"
    internal const val DEFAULT_DELAY_MINUTES = 45L

    fun schedule(
        context: Context,
        snapshot: WeatherSnapshot? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        WeatherWidgetRefreshScheduler.schedule(context, snapshot, nowEpochMillis)
        enqueue(
            context = context,
            delayMinutes = snapshot?.let {
                refreshCadenceMinutes(it.forecast, nowEpochMillis)
            } ?: DEFAULT_DELAY_MINUTES,
            policy = ExistingWorkPolicy.REPLACE,
        )
    }

    internal fun scheduleNext(
        context: Context,
        snapshot: WeatherSnapshot?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        WeatherWidgetRefreshScheduler.schedule(context, snapshot, nowEpochMillis)
        enqueue(
            context = context,
            delayMinutes = snapshot?.let {
                refreshCadenceMinutes(it.forecast, nowEpochMillis)
            } ?: DEFAULT_DELAY_MINUTES,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
    }

    private fun enqueue(
        context: Context,
        delayMinutes: Long,
        policy: ExistingWorkPolicy,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES,
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }
}

internal class WeatherRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = WeatherRepositoryFactory.create(applicationContext)
        return when (val result = repository.refresh(force = true, updateLocation = false)) {
            is RefreshResult.Updated -> {
                WeatherWidget.updateAll(applicationContext)
                RainNotificationManager.notifyIfMeaningful(applicationContext, result.snapshot)
                WeatherRefreshScheduler.scheduleNext(applicationContext, result.snapshot)
                Result.success()
            }
            RefreshResult.Skipped -> {
                WeatherRefreshScheduler.scheduleNext(
                    applicationContext,
                    repository.state.value.snapshot,
                )
                Result.success()
            }
            is RefreshResult.Failed -> {
                if (!result.retryable) {
                    WeatherRefreshScheduler.schedule(applicationContext, repository.state.value.snapshot)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }
}

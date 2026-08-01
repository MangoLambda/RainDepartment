package com.raindepartment.weather

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

internal object WeatherRefreshScheduler {
    internal const val WORK_NAME = "weather_refresh"
    internal const val INTERVAL_HOURS = 6

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(INTERVAL_HOURS.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES,
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
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
                Result.success()
            }
            RefreshResult.Skipped -> Result.success()
            is RefreshResult.Failed -> if (result.retryable) Result.retry() else Result.success()
        }
    }
}

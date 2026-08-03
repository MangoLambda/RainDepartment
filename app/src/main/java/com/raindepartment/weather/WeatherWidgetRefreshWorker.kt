package com.raindepartment.weather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

internal const val WIDGET_REFRESH_INTERVAL_MINUTES = 6L
private const val WIDGET_RAIN_START_MAX_MINUTES = 60L

internal fun shouldScheduleWidgetRefresh(
    forecast: DashboardForecast,
    nowEpochMillis: Long,
): Boolean = forecast.rainStartMinutesFromNow(nowEpochMillis)?.let { minutes ->
    minutes in 1L..WIDGET_RAIN_START_MAX_MINUTES
} == true

internal fun hasWeatherWidget(context: Context): Boolean =
    AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, WeatherWidgetReceiver::class.java),
    ).isNotEmpty()

internal object WeatherWidgetRefreshScheduler {
    internal const val WORK_NAME = "weather_widget_refresh"

    fun schedule(
        context: Context,
        snapshot: WeatherSnapshot?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        if (snapshot == null ||
            !hasWeatherWidget(appContext) ||
            !shouldScheduleWidgetRefresh(snapshot.forecast, nowEpochMillis)
        ) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        enqueue(workManager, ExistingWorkPolicy.REPLACE)
    }

    internal fun scheduleNext(
        context: Context,
        snapshot: WeatherSnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val appContext = context.applicationContext
        if (!hasWeatherWidget(appContext) ||
            !shouldScheduleWidgetRefresh(snapshot.forecast, nowEpochMillis)
        ) {
            return
        }

        enqueue(
            WorkManager.getInstance(appContext),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
    }

    private fun enqueue(
        workManager: WorkManager,
        policy: ExistingWorkPolicy,
    ) {
        val request = OneTimeWorkRequestBuilder<WeatherWidgetRefreshWorker>()
            .setInitialDelay(WIDGET_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }
}

internal class WeatherWidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val snapshot = SharedPreferencesWeatherCache(context).read() ?: return Result.success()
        val nowEpochMillis = System.currentTimeMillis()
        if (!hasWeatherWidget(context)) return Result.success()

        // Re-render once even when the countdown reaches zero, then the scheduler stops.
        WeatherWidget.updateAll(context)
        WeatherWidgetRefreshScheduler.scheduleNext(context, snapshot, nowEpochMillis)
        return Result.success()
    }
}

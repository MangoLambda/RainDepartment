package com.raindepartment.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object RainNotificationManager {
    private const val CHANNEL_ID = "rain_start"
    private const val CHANNEL_NAME = "Rain start"
    private const val NOTIFICATION_ID = 3101
    private const val PREFERENCES_NAME = "rain_notifications"
    private const val LAST_EVENT_KEY = "last_event"
    private const val EVENT_BUCKET_MILLIS = 30 * 60 * 1_000L

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun notifyIfMeaningful(
        context: Context,
        snapshot: WeatherSnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val forecast = snapshot.forecast
        val startsInMinutes = forecast.rainStartMinutesFromNow(nowEpochMillis) ?: return false
        if (forecast.rainStartSource != RainStartSource.ECCC_RADAR ||
            !forecast.rainStartConfidenceMeaningful ||
            startsInMinutes > 60L ||
            !hasPermission(context)
        ) {
            return false
        }

        val startsAt = forecast.rainStartsAtEpochMillis ?: return false
        val eventKey = buildString {
            append(snapshot.location.latitude)
            append(':')
            append(snapshot.location.longitude)
            append(':')
            append(startsAt / EVENT_BUCKET_MILLIS)
        }
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_EVENT_KEY, null) == eventKey) return false

        createChannel(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_precipitation)
            .setContentTitle(
                if (startsInMinutes == 0L) {
                    if (forecast.isCurrentlyRaining(nowEpochMillis)) {
                        "Rain is falling now"
                    } else {
                        "Rain expected soon"
                    }
                } else {
                    "Rain starts in ${forecast.rainStartCountdownText(nowEpochMillis)}"
                },
            )
            .setContentText("${forecast.location} · ECCC radar nowcast is meaningful")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            preferences.edit().putString(LAST_EVENT_KEY, eventKey).apply()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Meaningful ECCC radar rain-start updates"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

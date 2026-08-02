package com.raindepartment.weather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherRefreshWorkerTest {
    @Test
    fun schedulerEnqueuesConnectedCadencedWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        WeatherRefreshScheduler.schedule(context)

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WeatherRefreshScheduler.WORK_NAME)
            .get()

        assertEquals(1, work.size)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
        assertEquals(45L, WeatherRefreshScheduler.DEFAULT_DELAY_MINUTES)
    }
}

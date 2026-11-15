package com.habittags.app.backend

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.habittags.app.HabitTagsApp
import com.habittags.core.store.withDeviceId
import com.habittags.core.usage.afterReportSent
import com.habittags.core.usage.buildReport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class UsageReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (UsageReporting.sendNow(applicationContext)) Result.success() else Result.retry()
}

object UsageReporting {
    // The periodic and on-open workers can overlap; serialising sends keeps an older snapshot
    // from landing after a newer one.
    private val sending = Mutex()
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageReportWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("usage-report", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageReportWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("usage-report-now", ExistingWorkPolicy.KEEP, request)
    }

    /** Sends the current report; true when the server accepted it. */
    suspend fun sendNow(context: Context): Boolean = sending.withLock {
        val app = HabitTagsApp.from(context)
        val state = app.update { it.withDeviceId { UUID.randomUUID().toString() } }
        val deviceId = state.deviceId!!
        val now = Clock.System.now()
        val report = buildReport(state, now, TimeZone.currentSystemDefault())
        runCatching { app.backend.report(deviceId, report) }
            .onSuccess { app.update { it.afterReportSent(now.toEpochMilliseconds(), report) } }
            .isSuccess
    }
}

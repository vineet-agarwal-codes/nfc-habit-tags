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
import com.habittags.core.usage.afterReportSent
import com.habittags.core.usage.buildReport
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit

class UsageReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (UsageReporting.sendNow(applicationContext)) Result.success() else Result.retry()
}

object UsageReporting {
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
    suspend fun sendNow(context: Context): Boolean {
        val app = HabitTagsApp.from(context)
        val state = app.state.value
        val deviceId = state.deviceId ?: return false
        val now = Clock.System.now()
        return runCatching { app.backend.report(deviceId, buildReport(state, now, TimeZone.currentSystemDefault())) }
            .onSuccess {
                // Taps logged while sending are kept: only the outbox is cleared and the timestamp moved.
                app.update { it.afterReportSent(now.toEpochMilliseconds()) }
            }
            .isSuccess
    }
}

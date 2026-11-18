package com.goldlens.analyst.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MarketSignalScheduler {
    const val REPEAT_MINUTES = 15L

    fun start(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<MarketSignalWorker>(REPEAT_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            MarketSignalWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        val immediate = OneTimeWorkRequestBuilder<MarketSignalWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(MarketSignalWorker.IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, immediate)
    }

    /** Re-enqueues only the periodic scan (no immediate run) after reboot/force-stop; safe to call on every app start. */
    fun resumePeriodic(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<MarketSignalWorker>(REPEAT_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            MarketSignalWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    fun stop(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(MarketSignalWorker.PERIODIC_WORK)
        workManager.cancelUniqueWork(MarketSignalWorker.IMMEDIATE_WORK)
    }
}

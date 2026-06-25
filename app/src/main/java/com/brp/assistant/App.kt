package com.brp.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// FIX #2: App реализует Configuration.Provider для HiltWorkerFactory
// Это позволяет @HiltWorker-аннотированным Worker-классам (ModelDownloadWorker)
// получать зависимости через Hilt при создании WorkManager
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

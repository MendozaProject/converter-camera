package com.devsquid.convertercamera

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.devsquid.convertercamera.di.appModule
import com.devsquid.convertercamera.di.ocrModule
import com.devsquid.convertercamera.di.roomModule
import com.devsquid.convertercamera.service.RefreshWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import java.util.concurrent.TimeUnit

class Application : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(applicationContext)
            modules(roomModule, appModule, ocrModule)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connectivity
            .build()
        val refreshWorkRequest = PeriodicWorkRequest.Builder(
            RefreshWorker::class.java, 60, TimeUnit.MINUTES
        ).addTag(REFRESH_WORKER_TAG).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueue(refreshWorkRequest)
    }

    companion object {
        const val REFRESH_WORKER_TAG = "NetworkRefresh"
    }
}
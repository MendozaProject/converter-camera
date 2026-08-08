package com.devsquid.convertercamera.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.devsquid.convertercamera.remote.FetchCurrencyDataTask
import org.koin.java.KoinJavaComponent.inject

/**
 * Worker class that fetches currency data from Open Exchange Rates API.
 * Saves received data to database
 */
class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val fetchCurrencyDataTask: FetchCurrencyDataTask by inject(FetchCurrencyDataTask::class.java)

    override fun doWork(): Result {
        startFetchTask()
        return Result.success()
    }

    /**
     * Launches a coroutine that calls the api and reads and saves the received data into the
     * database.
     */
    private fun startFetchTask() {
        fetchCurrencyDataTask.run()
    }
}
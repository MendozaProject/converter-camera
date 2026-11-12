package com.devsquid.convertercamera.di

import androidx.core.content.ContextCompat
import androidx.room.Room.databaseBuilder
import com.devsquid.convertercamera.business.AITextScanner
import com.devsquid.convertercamera.business.MLKitTextScanner
import com.devsquid.convertercamera.business.PriceConverter
import com.devsquid.convertercamera.business.TextParser
import com.devsquid.convertercamera.business.TextScanner
import com.devsquid.convertercamera.database.AppDatabase
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.domain.CurrencyRepository
import com.devsquid.convertercamera.remote.ApiInterface
import com.devsquid.convertercamera.remote.FetchCurrencyDataTask
import com.devsquid.convertercamera.ui.camera.Camera
import com.devsquid.convertercamera.ui.camera.CameraViewModel
import com.devsquid.convertercamera.ui.selector.CurrencySelectorViewModel
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.concurrent.Executor


val roomModule = module {
    single {
        databaseBuilder(
            androidContext(),
            AppDatabase::class.java, "cache_database"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }
    single { get<AppDatabase>().currencyDataDao() }
}

val appModule = module {
    single { CurrencyRepository(get()) }
    single { ApiInterface() }
    single { TextParser() }
    single { FetchCurrencyDataTask(get(), get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { SessionRepository(get(), get(), androidContext()) }
    single { PriceConverter(get()) }
    single { Camera(get()) }
    viewModelOf(::CameraViewModel)
    viewModelOf(::CurrencySelectorViewModel)
}

val ocrModule = module {
    factory<TextRecognizer> { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    single<Executor> { ContextCompat.getMainExecutor(androidContext()) }
    factory<GenerativeModel> { com.google.mlkit.genai.prompt.Generation.getClient() }
    factory<MLKitTextScanner> { MLKitTextScanner(get(), get(), get()) }
    factory<AITextScanner> { AITextScanner(get(), get(), get()) }
}
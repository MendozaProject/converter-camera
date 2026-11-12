package com.devsquid.convertercamera

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devsquid.convertercamera.di.ocrModule
import com.google.mlkit.vision.text.TextRecognizer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class OcrModuleTest {

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        startKoin {
            androidContext(appContext)
            modules(ocrModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testTextRecognizerFactoryLifecycleAndRecreation() {
        // Get first TextRecognizer instance
        val recognizer1 = get().get<TextRecognizer>()
        assertNotNull(recognizer1)

        // Close the first instance simulating ViewModel.onCleared closing the detector
        recognizer1.close()

        // Get second TextRecognizer instance simulating reopening the app or returning to camera
        val recognizer2 = get().get<TextRecognizer>()
        assertNotNull(recognizer2)

        // Verify that a new distinct instance was created factory behavior
        assertNotSame(recognizer1, recognizer2)

        // Clean up second instance
        recognizer2.close()
    }

    @Test
    fun testScannersResolution() {
        val mlKitScanner = get().get<com.devsquid.convertercamera.business.MLKitTextScanner>()
        assertNotNull(mlKitScanner)
        mlKitScanner.close()

        val aiScanner = get().get<com.devsquid.convertercamera.business.AITextScanner>()
        assertNotNull(aiScanner)
        aiScanner.close()
    }
}

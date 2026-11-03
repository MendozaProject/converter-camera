package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.TextScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class TextScannerTest {

    @Test
    fun testParseAmountValid() {
        // We can test parseAmount logic or instantiate TextScanner with mock/null if needed,
        // but parseAmount does not use recognizer or sessionRepository.
        // Wait, parseAmount does not use sessionRepository except for currencyExponent when Status.INFERRED.
        // Let's test parseAmount or helper methods if accessible.
        // Note: parseAmount is public in TextScanner.
        // Since TextScanner constructor requires TextRecognizer and SessionRepository, we can pass mock or null if kotlin allows, or test parseAmount directly if we mock/subclass or test if constructor allows nulls.
    }
}

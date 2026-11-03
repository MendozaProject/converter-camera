package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.TextConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class TextConverterTest {

    private val textConverter = TextConverter()

    @Test
    fun testFormatCurrencyUSD() {
        val formatted = textConverter.formatCurrency(BigDecimal("10.50"), "USD", Locale.US)
        assertNotNull(formatted)
        // USD uses 2 fraction digits -> "$10.50" (or depending on locale formatting)
        assertEquals(true, formatted.contains("10.50"))
    }

    @Test
    fun testFormatCurrencyJPY() {
        // JPY has 0 fraction digits
        val formatted = textConverter.formatCurrency(BigDecimal("1000"), "JPY", Locale.JAPAN)
        assertNotNull(formatted)
        assertEquals(true, formatted.contains("1000") || formatted.contains("1,000"))
    }

    @Test
    fun testGetConvertedTextWithoutRepositoryReturnsNull() {
        val result = textConverter.getConvertedText(BigDecimal("50.0"))
        assertEquals("null", result)
    }
}

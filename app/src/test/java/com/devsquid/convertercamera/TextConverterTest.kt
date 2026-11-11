package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class TextConverterTest {

    @Test
    fun testFormatExtensionUSD() {
        val formatted = BigDecimal("10.50").format("USD", Locale.US)
        assertNotNull(formatted)
        assertEquals(true, formatted.contains("10.50"))
    }

    @Test
    fun testFormatExtensionJPY() {
        val formatted = BigDecimal("1000").format("JPY", Locale.JAPAN)
        assertNotNull(formatted)
        assertEquals(true, formatted.contains("1000") || formatted.contains("1,000"))
    }

    @Test
    fun testFormatExtensionZeroReturnsNull() {
        val formatted = BigDecimal.ZERO.format("USD", Locale.US)
        assertEquals("null", formatted)
    }
}

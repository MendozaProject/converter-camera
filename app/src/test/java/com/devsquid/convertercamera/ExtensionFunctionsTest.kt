package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ExtensionFunctionsTest {

    @Test
    fun testBigDecimalFormatValid() {
        val amount = BigDecimal("12.34")
        val formatted = amount.format("USD", Locale.US)
        assertEquals(true, formatted.contains("12.34"))
    }

    @Test
    fun testBigDecimalFormatZeroOrNegativeReturnsNull() {
        assertEquals("null", BigDecimal.ZERO.format("USD"))
        assertEquals("null", BigDecimal("-1.00").format("USD"))
    }

    @Test
    fun testBigDecimalFormatJpy() {
        val amount = BigDecimal("1500")
        val formatted = amount.format("JPY", Locale.JAPAN)
        assertEquals(true, formatted.contains("1500") || formatted.contains("1,500"))
    }
}

package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.HybridScanner.Companion.ocrSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrSliceTest {

    @Test
    fun keepsOcrSeparatorsRegardlessOfModelFormatting() {
        // Model answered "1.001" or "1001"; only its digits matter.
        assertEquals("1,001", ocrSlice("1,001", "1001"))
        assertEquals("1,001", ocrSlice("1,001円", "1001"))
    }

    @Test
    fun dropsMisreadLeadingSymbol() {
        assertEquals("74", ocrSlice("474)", "74"))
        assertEquals("1,001", ocrSlice("41,001", "1001"))
    }

    @Test
    fun dropsMisreadTrailingSymbol() {
        assertEquals("1000", ocrSlice("10007", "1000"))
        assertEquals("1,000", ocrSlice("1,0007", "1000"))
    }

    @Test
    fun rejectsDigitsNotInOcrText() {
        assertNull(ocrSlice("474", "75"))
        assertNull(ocrSlice("1,001", "2001"))
        assertNull(ocrSlice("74", ""))
    }
}

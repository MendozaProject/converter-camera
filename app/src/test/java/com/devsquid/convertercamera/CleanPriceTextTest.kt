package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.MLKitTextScanner.Companion.cleanPriceText
import com.devsquid.convertercamera.business.OcrScript
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanPriceTextTest {

    @Test
    fun stripsNonLatinCurrencySuffixes() {
        assertEquals("1,000", cleanPriceText("1,000円"))
        assertEquals("25", cleanPriceText("25元"))
        assertEquals("15,000", cleanPriceText("15,000원"))
        assertEquals("980", cleanPriceText("980円(税込)"))
    }

    @Test
    fun stripsCurrencySymbolPrefixes() {
        assertEquals("1,200", cleanPriceText("¥1,200"))
        assertEquals("1,200", cleanPriceText("￥1,200"))
        assertEquals("3.50", cleanPriceText("€3.50"))
        assertEquals("3.50", cleanPriceText("$3.50"))
    }

    @Test
    fun normalizesFullWidthDigitsAndSeparators() {
        assertEquals("1,000", cleanPriceText("１，０００円"))
    }

    @Test
    fun stripsNonNumericalSpecialCharacters() {
        assertEquals("70", cleanPriceText("70)"))
    }


    @Test
    fun keepsAsciiLettersAndSeparatorsSoParserRejectsThem() {
        assertEquals("A4", cleanPriceText("A4"))
        assertEquals(".99", cleanPriceText(".99"))
        assertEquals("1000.", cleanPriceText("1000.円"))
    }

    @Test
    fun picksScriptFromCurrency() {
        assertEquals(OcrScript.JAPANESE, OcrScript.forCurrency("JPY"))
        assertEquals(OcrScript.CHINESE, OcrScript.forCurrency("cny"))
        assertEquals(OcrScript.KOREAN, OcrScript.forCurrency("KRW"))
        assertEquals(OcrScript.LATIN, OcrScript.forCurrency("USD"))
        assertEquals(OcrScript.LATIN, OcrScript.forCurrency(""))
    }
}

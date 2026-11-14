package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.isPossibleYenSignMisread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PossibleYenSignMisreadTest {

    @Test
    fun flagsValuesStartingWithFourForYenSignCurrencies() {
        assertTrue(isPossibleYenSignMisread(BigDecimal("474"), "JPY"))
        assertTrue(isPossibleYenSignMisread(BigDecimal("4"), "JPY"))
        assertTrue(isPossibleYenSignMisread(BigDecimal("41001"), "CNY"))
        assertTrue(isPossibleYenSignMisread(BigDecimal("45.50"), "cny"))
    }

    @Test
    fun ignoresValuesNotStartingWithFour() {
        assertFalse(isPossibleYenSignMisread(BigDecimal("74"), "JPY"))
        assertFalse(isPossibleYenSignMisread(BigDecimal("1400"), "JPY"))
        assertFalse(isPossibleYenSignMisread(BigDecimal("0.45"), "CNY"))
    }

    @Test
    fun ignoresOtherCurrencies() {
        assertFalse(isPossibleYenSignMisread(BigDecimal("474"), "USD"))
        assertFalse(isPossibleYenSignMisread(BigDecimal("474"), "KRW"))
        assertFalse(isPossibleYenSignMisread(BigDecimal("474"), ""))
    }
}

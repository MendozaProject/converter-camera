package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.MLKitTextScanner.Companion.isLikelyNotPrice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikelyNotPriceTest {

    private fun notPrice(raw: String, previous: String? = null, next: String? = null) =
        assertTrue("expected '$raw' (prev=$previous, next=$next) to be flagged", isLikelyNotPrice(raw, previous, next))

    private fun price(raw: String, previous: String? = null, next: String? = null) =
        assertFalse("expected '$raw' (prev=$previous, next=$next) to be a price", isLikelyNotPrice(raw, previous, next))

    @Test
    fun percentages() {
        notPrice("5.3%")
        notPrice("20%")
        notPrice("20％")
        notPrice("8‰")
        notPrice("20", next = "%")
        notPrice("10", next = "%OFF")
    }

    @Test
    fun singleDecimalPlace() {
        notPrice("5.3")
        notPrice("4,5")
        notPrice("1.234,5")
    }

    @Test
    fun singleDecimalWithCurrencyMarkerIsPrice() {
        price("$5.5")
        price("5.5元")
        price("5.5", previous = "€")
        price("5.5", next = "円")
    }

    @Test
    fun manyDecimalPlaces() {
        notPrice("0.1234")
        notPrice("35.68950")
    }

    @Test
    fun unitsAndCounters() {
        notPrice("500", next = "g")
        notPrice("1.5", next = "L")
        notPrice("3", next = "pcs.")
        notPrice("2", next = "x")
        notPrice("25°")
        notPrice("3個")
        notPrice("10分")
        notPrice("2개")
        notPrice("3", next = "個入り")
        notPrice("3割")
    }

    @Test
    fun referenceMarkers() {
        notPrice("#12")
        notPrice("×2")
        notPrice("2", previous = "x")
        notPrice("5", previous = "Table")
        notPrice("12", previous = "No.")
        notPrice("4", previous = "Qty:")
        notPrice("3", previous = "数量")
        notPrice("第3")
    }

    @Test
    fun idsAndLongNumbers() {
        notPrice("007")
        notPrice("0123")
        notPrice("4901234567894")
        notPrice("12345678")
    }

    @Test
    fun ordinaryPricesAreNotFlagged() {
        price("74")
        price("1,200")
        price("1.200")
        price("3.50")
        price("0.50")
        price("1,000円")
        price("¥1,200")
        price("15,000원")
        price("1,234,567")
        price("1234567")
        price("500", previous = "Coffee", next = "Tea")
        price("980", next = "円")
        price("130", previous = "一本")
        price("74)")
    }
}

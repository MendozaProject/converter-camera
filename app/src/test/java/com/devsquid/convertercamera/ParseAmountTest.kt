package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.TextParser
import com.devsquid.convertercamera.business.TextParser.Companion.currencyExponent
import com.devsquid.convertercamera.business.TextParser.Status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ParseAmountTest {

    private val textParser = TextParser(null)

    /** Compares numerically (0.500 == 0.5) and checks the status. */
    private fun assertParsed(raw: String, currency: String, expected: String, status: Status) {
        val result = textParser.parseAmount(raw, currency)
        assertEquals("status for '$raw' $currency", status, result.status)
        val value = result.value
        assertTrue(
            "value for '$raw' $currency: expected $expected but was $value",
            value != null && value.compareTo(BigDecimal(expected)) == 0
        )
    }

    private fun assertMalformed(raw: String, currency: String = "USD") {
        val result = textParser.parseAmount(raw, currency)
        assertEquals("status for '$raw'", Status.MALFORMED, result.status)
        assertNull("value for '$raw'", result.value)
    }

    // ---------- Unambiguous inputs ----------

    @Test
    fun `plain integer has no separator`() =
        assertParsed("1234", "USD", "1234", Status.OK)

    @Test
    fun `US format with thousands and decimals`() =
        assertParsed("1,234.56", "USD", "1234.56", Status.OK)

    @Test
    fun `European format with thousands and decimals`() =
        assertParsed("1.234,56", "EUR", "1234.56", Status.OK)

    @Test
    fun `single separator with two trailing digits is a decimal`() =
        assertParsed("12,34", "EUR", "12.34", Status.OK)

    @Test
    fun `single separator with one trailing digit is a decimal`() =
        assertParsed("5.5", "USD", "5.5", Status.OK)

    @Test
    fun `single separator with four trailing digits is a decimal`() =
        assertParsed("1.2345", "CLF", "1.2345", Status.OK)

    @Test
    fun `repeated separators with three-digit tail are all thousands`() {
        assertParsed("1.234.567", "EUR", "1234567", Status.OK)
        assertParsed("1,234,567", "USD", "1234567", Status.OK)
    }

    @Test
    fun `repeated separators with short tail keep last as decimal`() {
        assertParsed("1,234,567.89", "USD", "1234567.89", Status.OK)
        assertParsed("1.234.56", "EUR", "1234.56", Status.OK)
    }

    // ---------- The three-digit ambiguity ----------

    @Test
    fun `three trailing digits in a 3-decimal currency is a decimal`() {
        assertParsed("1,234", "KWD", "1.234", Status.INFERRED)
        assertParsed("1.234", "BHD", "1.234", Status.INFERRED)
    }

    @Test
    fun `three trailing digits in a 0-decimal currency is thousands`() =
        assertParsed("1.234", "JPY", "1234", Status.INFERRED)

    @Test
    fun `three trailing digits in a 2-decimal currency is thousands`() {
        assertParsed("1.234", "USD", "1234", Status.INFERRED)
        assertParsed("12,345", "EUR", "12345", Status.INFERRED)
    }

    @Test
    fun `leading zero head forces a decimal regardless of currency`() {
        assertParsed("0.500", "KWD", "0.5", Status.OK)
        assertParsed("0,500", "USD", "0.5", Status.OK)
    }

    @Test
    fun `head longer than three digits forces a decimal regardless of currency`() {
        assertParsed("1234.567", "KWD", "1234.567", Status.OK)
        assertParsed("1234.567", "USD", "1234.567", Status.OK)
    }

    // ---------- Separator normalization and stripping ----------

    @Test
    fun `comma and period are interchangeable for OCR confusion`() {
        assertParsed("12,34", "EUR", "12.34", Status.OK)
        assertParsed("12.34", "EUR", "12.34", Status.OK)
        assertParsed("1,234", "KWD", "1.234", Status.INFERRED)
        assertParsed("1.234", "KWD", "1.234", Status.INFERRED)
    }

    @Test
    fun `middle dot is treated as a separator`() =
        assertParsed("1·50", "EUR", "1.50", Status.OK)

    @Test
    fun `spaces and non-breaking spaces are stripped`() {
        assertParsed("1 234,56", "EUR", "1234.56", Status.OK)
        assertParsed("1\u00A0234,56", "EUR", "1234.56", Status.OK)
    }

    @Test
    fun `apostrophe thousands separators are stripped`() {
        assertParsed("1'234.50", "CHF", "1234.50", Status.OK)
        assertParsed("1’234.50", "CHF", "1234.50", Status.OK)
    }

    // ---------- Malformed input ----------

    @Test
    fun `non-digit characters are malformed`() {
        assertMalformed("abc")
        assertMalformed("12-34")
        assertMalformed("\$5")
        assertMalformed("1,2O0") // letter O from OCR
    }

    @Test
    fun `empty and separator-only fragments are malformed`() {
        assertMalformed("")
        assertMalformed("   ")
        assertMalformed("1.")
        assertMalformed(".5")
        assertMalformed("1..5")
    }

    @Test
    fun `non-ASCII digits are malformed`() =
        assertMalformed("١٢٣")

    @Test
    fun `bad thousands grouping is malformed`() {
        assertMalformed("12.34.567")
        assertMalformed("1,23,456", "INR") // Indian grouping, not supported
        assertMalformed("1234.567.890")
    }

    // ---------- currencyExponent ----------

    @Test
    fun `currencyExponent returns ISO minor units`() {
        assertEquals(2, currencyExponent("USD"))
        assertEquals(2, currencyExponent("EUR"))
        assertEquals(0, currencyExponent("JPY"))
        assertEquals(0, currencyExponent("KRW"))
        assertEquals(3, currencyExponent("KWD"))
        assertEquals(3, currencyExponent("BHD"))
        assertEquals(3, currencyExponent("OMR"))
    }

    @Test
    fun `currencyExponent falls back to 2 for pseudo and unknown codes`() {
        assertEquals(2, currencyExponent("XXX")) // defaultFractionDigits == -1
        assertEquals(2, currencyExponent("ZZZ")) // not a real code
    }

    @Test
    fun `unknown currency behaves like a 2-decimal currency in the ambiguous case`() =
        assertParsed("1.234", "ZZZ", "1234", Status.INFERRED)

    // ---------- Minor-unit conversion ----------

    @Test
    fun `movePointRight then longValueExact catches excess precision`() {
        val ok = textParser.parseAmount("1.234", "KWD").value!!
        assertEquals(1234L, ok.movePointRight(3).longValueExact())

        val tooPrecise = textParser.parseAmount("1.2345", "KWD").value!!
        assertThrows(ArithmeticException::class.java) {
            tooPrecise.movePointRight(3).longValueExact()
        }
    }
}

package com.devsquid.convertercamera

import com.devsquid.convertercamera.business.PriceConverter
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.domain.CurrencyRate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PriceConverterTest {

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val priceConverter = PriceConverter(sessionRepository)

    @Test
    fun testConvertPrice() {
        every { sessionRepository.convertCurrency(any()) } returns BigDecimal("45.00")
        val converted = priceConverter.convertPrice(BigDecimal("15.00"))
        assertEquals(0, converted.compareTo(BigDecimal("45.00")))
    }

    @Test
    fun testFromAndToIsoCode() {
        val stateFlow = MutableStateFlow<SessionRepository.CurrencyDataState?>(
            SessionRepository.CurrencyDataState(
                fromCurrencyData = CurrencyRate("USD", "US Dollar", "$", BigDecimal.ONE, 2),
                toCurrencyData = CurrencyRate("EUR", "Euro", "€", BigDecimal("0.85"), 2)
            )
        )
        every { sessionRepository.currencyDataState } returns stateFlow

        assertEquals("USD", priceConverter.fromIsoCode())
        assertEquals("EUR", priceConverter.toIsoCode())
    }
}

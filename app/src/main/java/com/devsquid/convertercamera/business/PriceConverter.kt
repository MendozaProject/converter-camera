package com.devsquid.convertercamera.business

import com.devsquid.convertercamera.database.SessionRepository
import java.math.BigDecimal

class PriceConverter(val sessionRepository: SessionRepository) {

    fun toIsoCode() = sessionRepository.currencyDataState.value?.toCurrencyData?.isoCode ?: ""
    fun fromIsoCode() = sessionRepository.currencyDataState.value?.fromCurrencyData?.isoCode ?: ""

    fun convertPrice(amount: BigDecimal): BigDecimal {
        return sessionRepository.convertCurrency(amount)
    }
}
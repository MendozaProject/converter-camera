package com.devsquid.convertercamera.business

import com.devsquid.convertercamera.database.SessionRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class TextConverter(val sessionRepository: SessionRepository) {

    fun getConvertedText(amount: BigDecimal): String {
        val convertedAmount = sessionRepository.convertCurrency(amount)
        val data = sessionRepository.getToCurrencyData()

        return if (data != null && convertedAmount >= BigDecimal.ZERO)
            formatCurrency(convertedAmount, data.isoCode) else "null"
    }

    fun formatCurrency(
        amount: BigDecimal,
        currencyCode: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val currency = Currency.getInstance(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        format.minimumFractionDigits = currency.defaultFractionDigits
        format.maximumFractionDigits = currency.defaultFractionDigits
        return format.format(amount)
    }

    companion object {
        private const val TAG = "TextConverter"
    }
}
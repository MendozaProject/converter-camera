package com.devsquid.convertercamera.business

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun BigDecimal.format(currencyCode: String, locale: Locale = Locale.getDefault()): String {
    if (this <= BigDecimal.ZERO) return "null"
    val currency = Currency.getInstance(currencyCode)
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    format.minimumFractionDigits = currency.defaultFractionDigits
    format.maximumFractionDigits = currency.defaultFractionDigits
    return format.format(this)
}
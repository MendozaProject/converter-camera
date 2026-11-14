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

/** Currencies whose prices are written with ¥/￥. */
private val YEN_SIGN_CURRENCIES = setOf("JPY", "CNY")

/**
 * True if [value] may include a misread yen sign: OCR sometimes reads ¥/￥ as "4" ("￥74" ->
 * "474"), so for JPY and CNY any value starting with 4 is flagged for the user to check.
 */
fun isPossibleYenSignMisread(value: BigDecimal, fromIsoCode: String): Boolean =
    fromIsoCode.uppercase() in YEN_SIGN_CURRENCIES && value.abs().toPlainString().startsWith('4')
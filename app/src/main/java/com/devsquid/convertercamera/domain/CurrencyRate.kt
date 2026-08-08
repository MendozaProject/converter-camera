package com.devsquid.convertercamera.domain

/**
 * Domain layer object for storing Currency data as well as the rate against the US Dollar
 *
 * @param   isoCode the ISO code for the currency
 * @param   name    the name for the currency
 * @param   symbol  the symbol for the currency (if available)
 * @param   rateAgainstUsd  the amount for the currency compared to $1.00 USD
 * @param   decimalPlaces the number of the decimal places the currency uses, usually 0 or 2
 */
data class CurrencyRate (
    val isoCode: String,
    val name: String,
    val symbol: String,
    val rateAgainstUsd: Double,
    val decimalPlaces: Int
)
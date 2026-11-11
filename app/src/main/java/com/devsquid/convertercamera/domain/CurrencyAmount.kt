package com.devsquid.convertercamera.domain

import java.math.BigDecimal

/**
 * Domain layer object for storing Currency data as well as a converted amount
 *
 * @param   isoCode the ISO code for the currency
 * @param   name    the name for the currency
 * @param   symbol  the symbol for the currency (if available)
 * @param   amount  the amount of the currency to represent
 */
data class CurrencyAmount (
    val isoCode: String,
    val name: String,
    val symbol: String = "",
    val amount: BigDecimal,
)
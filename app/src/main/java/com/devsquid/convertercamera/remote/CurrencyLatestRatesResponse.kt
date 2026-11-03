package com.devsquid.convertercamera.remote

import java.math.BigDecimal

data class CurrencyLatestRatesResponse(
    val timestamp: Long,
    val base: String,
    val rates: Map<String, BigDecimal>
)

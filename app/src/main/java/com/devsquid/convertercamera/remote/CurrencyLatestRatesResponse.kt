package com.devsquid.convertercamera.remote

data class CurrencyLatestRatesResponse(
    val timestamp: Long,
    val base: String,
    val rates: Map<String, Double>
)
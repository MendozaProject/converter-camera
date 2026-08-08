package com.devsquid.convertercamera.business

import com.devsquid.convertercamera.domain.CurrencyAmount
import com.devsquid.convertercamera.domain.CurrencyRate


/**
 * Use case class for converting a list of currencies based on a set map of [CurrencyRate] providing
 * current rates against the US Dollar.
 */
class ConvertCurrenciesUseCase {

    /**
     * Map that stores the [CurrencyRate] items which contains the metadata about the currency as
     * well as the rate against the USD
     */
    var currencyRatesMap= mapOf<String, CurrencyRate>()

    /**
     * Performs a conversion locally based on saved USD values
     *
     * @param   convertFromIsoCode     the 3-letter ISO code of the currency to convert from
     * @param   amount      the monetary amount of the currency to convert
     */
    fun convertCurrencyList(convertFromIsoCode: String, amount: Double): Map<String, CurrencyAmount>?{
        val fromCurrencyData = currencyRatesMap[convertFromIsoCode] ?: return null
        val amountInUsd = amount / fromCurrencyData.rateAgainstUsd
        val convertedCurrencyMap = currencyRatesMap.map { (isoCode, currency) ->
            isoCode to CurrencyAmount(
                isoCode = currency.isoCode,
                name = currency.name,
                symbol = currency.symbol,
                amount = amountInUsd * currency.rateAgainstUsd,
            )
        }.toMap()

        return convertedCurrencyMap
    }


    /**
     * Performs a conversion locally based on saved USD values
     *
     * @param   convertFromIsoCode     the 3-letter ISO code of the currency to convert from
     * @param   amount      the monetary amount of the currency to convert
     */
    fun convertCurrency(convertFromIsoCode: String, convertToIsoCode: String, amount: Double): Double? {
        val fromCurrencyData = currencyRatesMap[convertFromIsoCode] ?: return null
        val toCurrencyData = currencyRatesMap[convertToIsoCode] ?: return null
        val amountInUsd = amount / fromCurrencyData.rateAgainstUsd

        return amountInUsd * toCurrencyData.rateAgainstUsd
    }
}
package com.devsquid.convertercamera.remote

import com.devsquid.convertercamera.domain.CurrencyRate
import com.devsquid.convertercamera.domain.CurrencyRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Date

/**
 * Task for fetching
 */
class FetchCurrencyDataTask(
    private val currencyRepository: CurrencyRepository,
    private val apiInterface: ApiInterface
) {
    private var isAvailable = true

    private val coroutineExceptionHandler = CoroutineExceptionHandler{_, throwable ->
        throwable.printStackTrace()
    }
    private val scope = CoroutineScope(Dispatchers.IO + coroutineExceptionHandler)

    fun run() {
        if (isAvailable) {
            scope.launch {
                isAvailable = false
                val getCurrencies = scope.async { apiInterface.makeGetCurrenciesRequest() }
                val getRates = scope.async { apiInterface.makeGetRatesRequest() }
                val receivedCurrency = getCurrencies.await()
                val receivedRates = getRates.await()

                if (receivedCurrency != null && receivedRates != null) {
                    val currentIsoCodes = currencyRepository.getCurrencyIsoCodes()
                    val timeStamp = Date()
                    val newCurrencies = mutableListOf<CurrencyRate>()
                    val updateCurrencies = mutableListOf<CurrencyRate>()

                    receivedRates.forEach { (isoCode, rate) ->
                        val currencyName = receivedCurrency[isoCode]
                        if (!currencyName.isNullOrEmpty()) {
                            val currency = CurrencyRate(
                                isoCode = isoCode,
                                name = currencyName,
                                symbol = getCurrencySymbol(isoCode),
                                rateAgainstUsd = rate,
                                decimalPlaces = 2
                            )
                            if (isoCode in currentIsoCodes) {
                                updateCurrencies.add(currency)
                            } else {
                                newCurrencies.add(currency)
                            }
                        }
                    }
                    currencyRepository.insertCurrencyData(newCurrencies, timeStamp)
                    currencyRepository.updateCurrencyData(updateCurrencies, timeStamp)
                }
                isAvailable = true
            }
        }
    }

    /**
     * Returns the currency symbol for the currency corresponding to the passed in ISO Code.
     *
     * @param   isoCode ISO code for the currency to get the symbol for
     * @return  the currency symbol if it is able to be retrieved from the ISO code or an empty string
     *          otherwise.
     */
    private fun getCurrencySymbol(isoCode: String): String {
        try {
            val currency = Currency.getInstance(isoCode)
            return currency.symbol
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }


    private fun getDecimalPlaces(isoCode: String): Int {
        return when (isoCode.lowercase()) {
            "jpy" -> 0
            else -> 2
        }
    }
}
package com.devsquid.convertercamera.domain

import com.devsquid.convertercamera.database.CurrencyData
import com.devsquid.convertercamera.database.CurrencyDataDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Repository for exposing data from the Currency database
 */
class CurrencyRepository(private val currencyDataDao: CurrencyDataDao) {

    /**
     * Updates currency data on the database layer
     *
     * @param   currencyRates    the domain layer object containing currency information
     */
    suspend fun insertCurrencyData(currencyRates: List<CurrencyRate>, timestamp: Date) {
        currencyDataDao.insertCurrency(currencyRates.map {
            it.toCurrencyData(timestamp)
        })
    }

    /**
     * Updates currency data on the database layer
     *
     * @param   currencyRates    the domain layer object containing currency information
     */
    suspend fun updateCurrencyData(currencyRates: List<CurrencyRate>, timestamp: Date) {
        currencyDataDao.updateCurrency(currencyRates.map {
            it.toCurrencyData(timestamp)
        })
    }

    /**
     * Returns the IsoCodes for all the currencies in the database
     */
    fun getCurrencyIsoCodes(): Collection<String> = currencyDataDao.getAllCurrencyIsoCodes()

    /**
     * Returns the IsoCodes for all the currencies in the database
     */
    suspend fun getCurrencyRateFromIsoCode(isoCode: String): CurrencyRate =
        currencyDataDao.getCurrency(isoCode).toCurrencyRate()


    /**
     * Exposes changes in the Currency Data database
     *
     * @return  [Flow] that emits updates to the currency database
     */
    fun observeCurrencies(): Flow<Map<String, CurrencyRate>> =
        currencyDataDao.currencyFlow().map {
            it.associate { c ->
                c.isoCode to c.toCurrencyRate()
            }
        }

    /**
     * Extension function to convert domain layer [CurrencyRate] to database layer [CurrencyData]
     *
     * @param timestamp the time of object creation
     */
    private fun CurrencyRate.toCurrencyData(timestamp: Date) = CurrencyData(
        isoCode, name, symbol, rateAgainstUsd, timestamp, decimalPlaces
    )

    /**
     * Extension function to convert database layer [CurrencyData] to domain layer [CurrencyRate]
     */
    private fun CurrencyData.toCurrencyRate() = CurrencyRate(
        isoCode, name, symbol, rate, decimalPlaces
    )
}
package com.devsquid.convertercamera.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.devsquid.convertercamera.FROM_CURRENCY_ISO
import com.devsquid.convertercamera.TO_CURRENCY_ISO
import com.devsquid.convertercamera.domain.CurrencyRate
import com.devsquid.convertercamera.domain.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class SessionRepository(
    currencyRepository: CurrencyRepository,
    appScope: CoroutineScope,
    private val context: Context
) {
    private val _session = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _session

    val currencyDataState: StateFlow<CurrencyDataState?> =
        combine(currencyRepository.observeCurrencies(), _session) { rates, session ->
            if (rates.isNotEmpty()) {
                val fromCurrency = rates[session.fromCurrencyISO]
                val toCurrency = rates[session.toCurrencyISO]
                if (fromCurrency != null && toCurrency != null) {
                    CurrencyDataState(
                        fromCurrencyData = fromCurrency,
                        toCurrencyData = toCurrency
                    )
                } else null
            } else null
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    init {
        appScope.launch {
            _session.collect { session ->
                context.dataStore.edit {
                    it[FROM_CURRENCY_ISO] = session.fromCurrencyISO
                    it[TO_CURRENCY_ISO] = session.toCurrencyISO
                }
            }
        }
    }

    fun setToCurrency(isoCode: String) {
        if (isoCode == _session.value.fromCurrencyISO) {
            swapCurrency()
        } else {
            _session.update { it.copy(toCurrencyISO = isoCode) }
        }
    }

    fun setFromCurrency(isoCode: String) {
        if (isoCode == _session.value.toCurrencyISO) {
            swapCurrency()
        } else {
            _session.update { it.copy(fromCurrencyISO = isoCode)}
        }
    }

    fun swapCurrency() {
        _session.update {
            it.copy(toCurrencyISO = it.fromCurrencyISO, fromCurrencyISO = it.toCurrencyISO)
        }
    }

    fun convertCurrency(amount: BigDecimal): BigDecimal {
        return currencyDataState.value?.convertPrice(amount) ?: BigDecimal("-1.0")
    }

    fun getToCurrencyData(): CurrencyRate? {
        return currencyDataState.value?.toCurrencyData
    }

    fun getFromCurrencyData(): CurrencyRate? {
        return currencyDataState.value?.fromCurrencyData
    }

    data class SessionState(
        val fromCurrencyISO: String = "JPY",
        val toCurrencyISO: String = "USD",
        val decimalDelimiter: Char = '.'
    )

    // This is the volatile and just saved in memory
    data class CurrencyDataState(
        val fromCurrencyData: CurrencyRate? = null,
        val toCurrencyData: CurrencyRate? = null,
    ) {
        val conversionRate: BigDecimal =
            if (toCurrencyData != null && fromCurrencyData != null && fromCurrencyData.rateAgainstUsd > BigDecimal.ZERO)
                toCurrencyData.rateAgainstUsd.divide(fromCurrencyData.rateAgainstUsd, 10, RoundingMode.HALF_UP)
            else BigDecimal("-1.0")

        fun convertPrice(amount: BigDecimal): BigDecimal {
            return if (conversionRate >= BigDecimal.ZERO) amount.multiply(conversionRate) else BigDecimal("-1.0")
        }

        fun formatToCurrency(amount: BigDecimal): String {
            val isoCode = toCurrencyData?.isoCode
            return if (isoCode != null) amount.format(isoCode) else "null"
        }

        fun formatFromCurrency(amount: BigDecimal): String {
            val isoCode = fromCurrencyData?.isoCode
            return if (isoCode != null) amount.format(isoCode) else "null"
        }

        fun convertAndFormatPrice(amount: BigDecimal): String {
            return formatToCurrency(convertPrice(amount))
        }

        fun BigDecimal.format(currencyCode: String, locale: Locale = Locale.getDefault()): String {
            if (this <= BigDecimal.ZERO) return "null"
            val currency = Currency.getInstance(currencyCode)
            val format = NumberFormat.getCurrencyInstance(locale)
            format.currency = currency
            format.minimumFractionDigits = currency.defaultFractionDigits
            format.maximumFractionDigits = currency.defaultFractionDigits
            return format.format(this)
        }
    }

    companion object {
        private const val TAG = "SessionRepository"
    }
}
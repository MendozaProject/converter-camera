package com.devsquid.convertercamera.ui.selector

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.domain.CurrencyRepository
import com.devsquid.convertercamera.ui.CurrencyListItemData
import com.devsquid.convertercamera.ui.MODE_SET_CONVERT_CURRENCY
import com.devsquid.convertercamera.ui.MODE_SET_ORIGINAL_CURRENCY
import com.devsquid.convertercamera.ui.SelectorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencySelectorViewModel(
    val currencyRepository: CurrencyRepository,
    val sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle): ViewModel() {

    val mode: Int = savedStateHandle.toRoute<SelectorRoute>().mode

    private val _filterQuery = MutableStateFlow("")
    val filterQuery = _filterQuery.asStateFlow()

    private val _currenciesState = MutableStateFlow<List<CurrencyListItemData>>(emptyList())
    val currenciesState = combine(_currenciesState, _filterQuery) { currencies, query ->
        if (query.isBlank()) {
            currencies
        } else {
            currencies.filter {
                it.isoCode.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.currencySymbol.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            currencyRepository.observeCurrencies().collect {
                if (it.isNotEmpty()) {
                    _currenciesState.value = it.map {
                        c -> CurrencyListItemData(isoCode = c.key, name = c.value.name)
                    }
                } else {
                    Log.e(TAG, "observeCurrencyData: currency rates list is empty")
                }
            }
        }
    }

    fun onSelect(isoCode: String) {
        when (mode) {
            MODE_SET_ORIGINAL_CURRENCY -> sessionRepository.setToCurrency(isoCode)
            MODE_SET_CONVERT_CURRENCY -> sessionRepository.setFromCurrency(isoCode)
            else -> Log.e(TAG, "Illegal argument")
        }
    }

    fun onFilter(query: String) {
        _filterQuery.value = query
    }

    companion object {
        const val TAG = "CurrencySelectorViewModel"
    }
}
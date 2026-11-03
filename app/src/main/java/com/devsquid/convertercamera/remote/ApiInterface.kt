package com.devsquid.convertercamera.remote

import android.util.Log
import com.devsquid.convertercamera.BuildConfig
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.annotations.VisibleForTesting
import java.math.BigDecimal

class ApiInterface {

    private val client = OkHttpClient()

    /**
     * Asynchronous function for calling the currencies data API
     *
     * @return  a map containing the values of the currency iso code and name
     */
    suspend fun makeGetCurrenciesRequest(): Map<String, String>? {
        var currencyData: Map<String, String>? = null

        val request = Request.Builder().url("$BASE_URL$CURRENCIES_REQUEST").build()
        println(request.url)
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        if (response.isSuccessful) {
            val responseBody = response.body.string()
            currencyData = parseCurrenciesJson(responseBody)
        } else {
            println("Error: ${response.code}")
        }
        return currencyData
    }

    /**
     * Asynchronous function for calling the latest rates (against USD) API
     *
     * @return  a map containing the currency iso code and the current rate against USD
     */
    suspend fun makeGetRatesRequest(): Map<String, BigDecimal>? {
        var currencyRatesMap: Map<String, BigDecimal>? = null

        val request = Request.Builder().url("$BASE_URL$LATEST_REQUEST$APP_ID$API_KEY").build()
        println(request.url)
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            if (!responseBody.isNullOrEmpty()) {
                currencyRatesMap = parseLatestRatesJson(responseBody)
            } else {
                Log.e(TAG, "onResponse: : null response")
            }
        } else {
            Log.e(TAG, "Error: ${response.code}")
        }
        return currencyRatesMap
    }

    /**
     * Pareses the JSON response from the Supported Currencies API call.
     *
     * @param   responseBody    The JSON formatted string to be parsed
     */
    @VisibleForTesting
    fun parseCurrenciesJson(responseBody: String) : Map<String,String>? {
        println("Response: $responseBody")
        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java
        )
        val adapter: JsonAdapter<Map<String, String>> = Moshi
            .Builder()
            .build()
            .adapter(type)
        return adapter.fromJson(responseBody)
    }

    /**
     * Pareses the JSON response from the Latest Rates API call.
     *
     * @param   responseBody    The JSON formatted string to be parsed
     */
    @VisibleForTesting
    fun parseLatestRatesJson(responseBody: String): Map<String, BigDecimal>? {
        println("Response: $responseBody")
        val adapter = Moshi.Builder()
            .add(ApiUpdateAdapter())
            .build()
            .adapter(CurrencyLatestRatesResponse::class.java)
        val currencyLatestRatesResponse = adapter.fromJson(responseBody)
        return currencyLatestRatesResponse?.rates
    }

    companion object {
        private const val TAG = "ApiInterface"
        private const val BASE_URL = "https://openexchangerates.org/api/"
        private const val LATEST_REQUEST = "latest.json"
        private const val CURRENCIES_REQUEST = "currencies.json"
        private const val APP_ID = "?app_id="
        private const val API_KEY=BuildConfig.API_KEY
    }
}
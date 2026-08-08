package com.devsquid.convertercamera.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDataDao {

    @Insert
    suspend fun insertCurrency(currency: CurrencyData)

    @Insert
    suspend fun insertCurrency(currency: List<CurrencyData>)

    @Update
    suspend fun updateCurrency(currency: CurrencyData)

    @Update
    suspend fun updateCurrency(currency: List<CurrencyData>)

    @Query("SELECT * FROM CurrencyData WHERE isoCode=:isoCode")
    suspend fun getCurrency(isoCode: String): CurrencyData

    @Query("SELECT isoCode FROM CurrencyData")
    fun getAllCurrencyIsoCodes(): List<String>

    @Query("SELECT * FROM CurrencyData " +
            "WHERE name NOT NULL AND name != '' AND rate > 0 " +
            "ORDER BY isoCode ASC")
    fun currencyFlow(): Flow<List<CurrencyData>>
}
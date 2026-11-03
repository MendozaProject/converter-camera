package com.devsquid.convertercamera.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.Date

@Entity(tableName = "CurrencyData")
data class CurrencyData(
    @PrimaryKey val isoCode: String,
    val name: String,
    val symbol: String,
    val rate: BigDecimal = BigDecimal.ZERO,
    val timeStamp: Date,
    val decimalPlaces: Int
)
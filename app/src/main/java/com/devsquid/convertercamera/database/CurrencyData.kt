package com.devsquid.convertercamera.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "CurrencyData")
data class CurrencyData(
    @PrimaryKey val isoCode: String,
    val name: String,
    val symbol: String,
    val rate: Double = 0.0,
    val timeStamp: Date,
    val decimalPlaces: Int
)
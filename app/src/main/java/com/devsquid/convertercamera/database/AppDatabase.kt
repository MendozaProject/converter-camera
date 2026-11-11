package com.devsquid.convertercamera.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [CurrencyData::class], version = 2)
@TypeConverters(DateConverter::class, BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun currencyDataDao(): CurrencyDataDao
}
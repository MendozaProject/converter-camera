package com.devsquid.convertercamera.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson


class ApiUpdateAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): CurrencyLatestRatesResponse {
        val map: MutableMap<String, Double> = mutableMapOf()

        var timestamp: Long = 0L
        var baseIsoCode: String =""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonReader.Token.NAME ->
                    when (val fieldName = reader.nextName()) {
                        "timestamp" -> timestamp = reader.nextLong()
                        "base" -> baseIsoCode = reader.nextString()
                        "rates" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val isoCode = reader.nextName()
                                map[isoCode] = reader.nextDouble()
                            }
                            reader.endObject()
                        }
                    }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return CurrencyLatestRatesResponse(timestamp, baseIsoCode, map)
    }

    @ToJson
    fun ToJson(writer: JsonWriter, out: CurrencyLatestRatesResponse) {
        throw UnsupportedOperationException()
    }
}
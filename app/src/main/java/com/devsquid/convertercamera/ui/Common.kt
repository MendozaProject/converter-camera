package com.devsquid.convertercamera.ui

data class CurrencyListItemData(
    val isoCode: String = "",
    val name: String = "",
    val currencySymbol: String = ""
)

fun countryCodeToFlagEmoji(countryCode: String): String {
    val base = 0x1F1E6 - 'A'.code
    return countryCode.uppercase().map { char ->
        String(Character.toChars(base + char.code))
    }.joinToString("")
}

/**
 * Formats the decimal value as a string. Ensures that all decimal numbers have at least 2 digits
 * (e.g. 0.00) and that the maximum decimal precision is 4 digits.
 *
 * @param   value   the decimal to be formatted
 * @return  the formatted decimal
 */
fun formatDecimal(value: Double): String {
    if (value == 0.0) {
        return "0"
    }
    val decimalString = value.toString()
    val decimalIndex = decimalString.indexOf('.')
    return when {
        decimalIndex == decimalString.length - 2 -> "${decimalString}0"
        decimalString.length - decimalIndex > 5 -> decimalString.substring(0, decimalIndex + 5)
        else -> decimalString
    }
}

const val MODE_SET_ORIGINAL_CURRENCY: Int = 0
const val MODE_SET_CONVERT_CURRENCY: Int = 1
package com.devsquid.convertercamera.business

import com.devsquid.convertercamera.database.SessionRepository
import java.math.BigDecimal
import java.util.Currency

class TextParser(val sessionRepository: SessionRepository? = null) {

    fun parseAmount(raw: String): ParsedAmount {
        val currency = sessionRepository?.getFromCurrencyData()?.isoCode
            ?: return ParsedAmount(null, Status.MALFORMED)
        return parseAmount(raw, currency)
    }

    fun parseAmount(raw: String, currency: String): ParsedAmount {
        val s = raw.replace(strip, "").replace(separators, ".")

        val parts = s.split(".")
        if (!parts.all { it.isAsciiDigits() }) return ParsedAmount(null, Status.MALFORMED)

        if (parts.size == 1) return ParsedAmount(BigDecimal(parts[0]), Status.OK)

        val head = parts.dropLast(1)
        val last = parts.last()

        // Multiple separators
        if (parts.size > 2) {
            return when {
                last.length == 3 && validGrouping(parts) ->
                    ParsedAmount(BigDecimal(parts.joinToString("")), Status.OK)

                last.length != 3 && validGrouping(head) ->
                    ParsedAmount(BigDecimal(head.joinToString("") + "." + last), Status.OK)

                else -> ParsedAmount(null, Status.MALFORMED)
            }
        }

        // Single separator
        val h = head[0]
        if (last.length != 3) return ParsedAmount(BigDecimal("$h.$last"), Status.OK)

        // Exactly 3 digits after the separator: the one true ambiguity
        if (h == "0" || h.length > 3) return ParsedAmount(BigDecimal("$h.$last"), Status.OK)

        return if (currencyExponent(currency) == 3)
            ParsedAmount(BigDecimal("$h.$last"), Status.INFERRED)
        else
            ParsedAmount(BigDecimal(h + last), Status.INFERRED)
    }

    enum class Status { OK, INFERRED, MALFORMED }

    data class ParsedAmount(
        val value: BigDecimal?,
        val status: Status,
    )

    companion object {
        private const val TAG = "TextParser"

        private val strip = Regex("""[\s\u00A0'’]""")
        private val separators = Regex("""[.,·]""")

        private fun String.isAsciiDigits() = isNotEmpty() && all { it in '0'..'9' }

        /** Thousands grouping: first group 1-3 digits, remaining groups exactly 3. */
        private fun validGrouping(groups: List<String>) =
            groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 }

        fun currencyExponent(code: String): Int =
            try {
                Currency.getInstance(code).defaultFractionDigits.takeIf { it >= 0 } ?: 2
            } catch (e: IllegalArgumentException) {
                2 // unknown code; pick a default or fail, depending on your needs
            }

    }
}

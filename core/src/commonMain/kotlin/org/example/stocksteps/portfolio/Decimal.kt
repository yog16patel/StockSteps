package org.example.stocksteps.portfolio

/** Exact base-ten arithmetic, eight fractional digits; multiplication/division round half-up.
 * Integer strings avoid platform-specific BigDecimal and overflow on Kotlin/Native.
 */
class Decimal private constructor(private val magnitude: String, private val negative: Boolean) : Comparable<Decimal> {
    operator fun plus(other: Decimal): Decimal = if (negative == other.negative) make(add(magnitude, other.magnitude), negative)
        else if (compare(magnitude, other.magnitude) >= 0) make(sub(magnitude, other.magnitude), negative)
        else make(sub(other.magnitude, magnitude), other.negative)
    operator fun unaryMinus() = make(magnitude, !negative)
    operator fun minus(other: Decimal) = this + -other
    operator fun times(other: Decimal): Decimal = rounded(multiply(magnitude, other.magnitude), SCALE, negative != other.negative)
    operator fun div(other: Decimal): Decimal {
        require(other != ZERO) { "Cannot divide by zero." }
        val (quotient, remainder) = divide(magnitude + "0".repeat(SCALE), other.magnitude)
        return make(if (compare(add(remainder, remainder), other.magnitude) >= 0) add(quotient, "1") else quotient, negative != other.negative)
    }
    /** One rounding step for proportional basis, without an overflowing intermediate amount. */
    fun multiplyDivide(multiplier: Decimal, divisor: Decimal): Decimal {
        require(divisor != ZERO) { "Cannot divide by zero." }
        val (quotient, remainder) = divide(multiply(magnitude, multiplier.magnitude), divisor.magnitude)
        return make(if (compare(add(remainder, remainder), divisor.magnitude) >= 0) add(quotient, "1") else quotient,
            (negative != multiplier.negative) != divisor.negative)
    }
    override fun compareTo(other: Decimal): Int = if (negative != other.negative) if (negative) -1 else 1
        else compare(magnitude, other.magnitude) * if (negative) -1 else 1
    override fun equals(other: Any?) = other is Decimal && magnitude == other.magnitude && negative == other.negative
    override fun hashCode() = magnitude.hashCode() * 31 + negative.hashCode()
    fun display(places: Int = 2): String {
        require(places in 0..SCALE)
        val digits = magnitude.padStart(SCALE + 1, '0')
        val discarded = SCALE - places
        var kept = if (discarded == 0) digits else digits.dropLast(discarded)
        if (discarded > 0 && digits[digits.length - discarded] >= '5') kept = add(kept, "1")
        kept = kept.padStart(places + 1, '0')
        val sign = if (negative && clean(kept) != "0") "-" else ""
        return sign + if (places == 0) kept else kept.dropLast(places) + "." + kept.takeLast(places)
    }
    override fun toString(): String {
        val digits = magnitude.padStart(SCALE + 1, '0')
        val fraction = digits.takeLast(SCALE).trimEnd('0')
        return (if (negative) "-" else "") + digits.dropLast(SCALE) + if (fraction.isEmpty()) "" else ".$fraction"
    }
    companion object {
        const val SCALE = 8
        val ZERO = Decimal("0", false)
        val ONE = parse("1")
        fun parse(value: String): Decimal {
            require(value.matches(Regex("-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,8})?"))) { "Use a decimal with up to eight fractional digits." }
            val unsigned = value.removePrefix("-").split('.')
            return make(unsigned[0] + unsigned.getOrElse(1) { "" }.padEnd(SCALE, '0'), value.startsWith('-'))
        }
        private fun clean(value: String) = value.trimStart('0').ifEmpty { "0" }
        private fun make(value: String, negative: Boolean): Decimal {
            val normalized = clean(value)
            require(normalized.length <= 26) { "Amount exceeds the supported range." }
            return Decimal(normalized, negative && normalized != "0")
        }
        private fun compare(a: String, b: String): Int = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
        private fun add(a: String, b: String): String {
            var carry = 0
            val result = StringBuilder()
            for (index in 0 until maxOf(a.length, b.length)) {
                val sum = (a.getOrNull(a.lastIndex - index)?.digitToInt() ?: 0) + (b.getOrNull(b.lastIndex - index)?.digitToInt() ?: 0) + carry
                result.append(sum % 10); carry = sum / 10
            }
            if (carry > 0) result.append(carry)
            return result.reverse().toString()
        }
        private fun sub(a: String, b: String): String {
            var borrow = 0
            val result = StringBuilder()
            for (index in a.indices) {
                var digit = a[a.lastIndex - index].digitToInt() - (b.getOrNull(b.lastIndex - index)?.digitToInt() ?: 0) - borrow
                borrow = if (digit < 0) 1 else 0
                if (digit < 0) digit += 10
                result.append(digit)
            }
            return clean(result.reverse().toString())
        }
        private fun multiply(a: String, b: String): String {
            var result = "0"
            b.reversed().forEachIndexed { index, digit ->
                var row = "0"
                repeat(digit.digitToInt()) { row = add(row, a) }
                result = add(result, row + "0".repeat(index))
            }
            return clean(result)
        }
        private fun divide(a: String, b: String): Pair<String, String> {
            var remainder = "0"
            val result = StringBuilder()
            a.forEach { digit ->
                remainder = clean(remainder + digit)
                var count = 0
                while (compare(remainder, b) >= 0) { remainder = sub(remainder, b); count++ }
                result.append(count)
            }
            return clean(result.toString()) to remainder
        }
        private fun rounded(value: String, places: Int, negative: Boolean): Decimal {
            val padded = value.padStart(places + 1, '0')
            val integer = padded.dropLast(places)
            return make(if (padded[padded.length - places] >= '5') add(integer, "1") else integer, negative)
        }
    }
}

object PortfolioFormat {
    fun amount(value: String?): String = value?.let { runCatching { Decimal.parse(it).display() }.getOrNull() } ?: "—"
}

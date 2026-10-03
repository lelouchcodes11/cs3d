package android.util

class Rational(val numerator: Int, val denominator: Int) : Number(), Comparable<Rational> {
    override fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()
    override fun toFloat(): Float = numerator.toFloat() / denominator.toFloat()
    override fun toInt(): Int = (toDouble()).toInt()
    override fun toLong(): Long = (toDouble()).toLong()
    override fun toByte(): Byte = toInt().toByte()
    override fun toShort(): Short = toInt().toShort()

    override fun compareTo(other: Rational): Int {
        val thisNum = numerator.toLong() * other.denominator.toLong()
        val otherNum = other.numerator.toLong() * denominator.toLong()
        return thisNum.compareTo(otherNum)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rational) return false
        return numerator == other.numerator && denominator == other.denominator
    }

    override fun hashCode(): Int = 31 * numerator + denominator
    override fun toString(): String = "$numerator/$denominator"
}

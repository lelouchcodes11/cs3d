package androidx.core.graphics

class Insets private constructor(
    @JvmField val left: Int,
    @JvmField val top: Int,
    @JvmField val right: Int,
    @JvmField val bottom: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is Insets && other.left == left && other.top == top && other.right == right && other.bottom == bottom

    override fun hashCode(): Int = ((left * 31 + top) * 31 + right) * 31 + bottom
    override fun toString(): String = "Insets{left=$left, top=$top, right=$right, bottom=$bottom}"

    companion object {
        @JvmField
        val NONE = Insets(0, 0, 0, 0)

        @JvmStatic
        fun of(left: Int, top: Int, right: Int, bottom: Int): Insets =
            if (left == 0 && top == 0 && right == 0 && bottom == 0) NONE else Insets(left, top, right, bottom)

        @JvmStatic
        fun max(a: Insets, b: Insets): Insets =
            of(maxOf(a.left, b.left), maxOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
}

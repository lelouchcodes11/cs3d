package androidx.media3.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

class CaptionStyleCompat(
    val foregroundColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.BLACK,
    val windowColor: Int = Color.TRANSPARENT,
    val edgeType: Int = EDGE_TYPE_NONE,
    val edgeColor: Int = Color.BLACK,
    val typeface: Typeface? = null
) {
    @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class EdgeType

    companion object {
        const val EDGE_TYPE_NONE = 0
        const val EDGE_TYPE_OUTLINE = 1
        const val EDGE_TYPE_DROP_SHADOW = 2
        const val EDGE_TYPE_RAISED = 3
        const val EDGE_TYPE_DEPRESSED = 4
        const val USE_TRACK_COLOR_SETTINGS = 1

        val DEFAULT = CaptionStyleCompat()

        fun createFromStyledAttributes(context: Context): CaptionStyleCompat = DEFAULT
    }
}

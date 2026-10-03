package android.text.format

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

open class DateFormat {
    companion object {
        /** Follows the OS locale's short time pattern, like Android's system 12/24h setting */
        @JvmStatic
        fun is24HourFormat(context: Context?): Boolean {
            val pattern = (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()
                ?: return false
            return pattern.contains('H') || pattern.contains('k')
        }

        @JvmStatic
        fun getTimeFormat(context: Context?): java.text.DateFormat =
            SimpleDateFormat(if (is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())

        @JvmStatic
        fun getDateFormat(context: Context?): java.text.DateFormat =
            java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.getDefault())

        @JvmStatic
        fun getMediumDateFormat(context: Context?): java.text.DateFormat =
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())

        @JvmStatic
        fun getLongDateFormat(context: Context?): java.text.DateFormat =
            java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault())

        @JvmStatic
        fun getBestDateTimePattern(locale: Locale, skeleton: String): String = skeleton

        @JvmStatic
        fun format(inFormat: CharSequence, inTimeInMillis: Long): CharSequence = format(inFormat, Date(inTimeInMillis))

        @JvmStatic
        fun format(inFormat: CharSequence, inDate: Date): CharSequence =
            SimpleDateFormat(inFormat.toString(), Locale.getDefault()).format(inDate)

        @JvmStatic
        fun format(inFormat: CharSequence, inDate: Calendar): CharSequence = format(inFormat, inDate.time)
    }
}

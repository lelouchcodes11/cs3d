package android.text.format

object DateUtils {
    @JvmStatic
    fun formatElapsedTime(elapsedSeconds: Long): String {
        return formatElapsedTime(null, elapsedSeconds)
    }

    @JvmStatic
    fun formatElapsedTime(recycle: StringBuilder?, elapsedSeconds: Long): String {
        var s = elapsedSeconds
        var m = s / 60
        s %= 60
        val h = m / 60
        m %= 60
        val sb = recycle ?: StringBuilder()
        sb.setLength(0)
        if (h > 0) {
            sb.append(String.format("%d:%02d:%02d", h, m, s))
        } else {
            sb.append(String.format("%02d:%02d", m, s))
        }
        return sb.toString()
    }
}

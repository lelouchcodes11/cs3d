package android.media

class AudioAttributes private constructor(
    private val usage: Int,
    private val contentType: Int,
    private val flags: Int,
) {
    fun getUsage(): Int = usage
    fun getContentType(): Int = contentType
    fun getFlags(): Int = flags

    class Builder {
        private var usage = USAGE_UNKNOWN
        private var contentType = CONTENT_TYPE_UNKNOWN
        private var flags = 0

        constructor()
        constructor(aa: AudioAttributes) {
            usage = aa.usage
            contentType = aa.contentType
            flags = aa.flags
        }

        fun setUsage(usage: Int): Builder = apply { this.usage = usage }
        fun setContentType(contentType: Int): Builder = apply { this.contentType = contentType }
        fun setFlags(flags: Int): Builder = apply { this.flags = flags }
        fun setLegacyStreamType(streamType: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes(usage, contentType, flags)
    }

    companion object {
        const val CONTENT_TYPE_UNKNOWN = 0
        const val CONTENT_TYPE_SPEECH = 1
        const val CONTENT_TYPE_MUSIC = 2
        const val CONTENT_TYPE_MOVIE = 3
        const val CONTENT_TYPE_SONIFICATION = 4
        const val USAGE_UNKNOWN = 0
        const val USAGE_MEDIA = 1
        const val USAGE_GAME = 14
        const val FLAG_AUDIBILITY_ENFORCED = 1
    }
}

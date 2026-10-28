package com.lagradost.desktop.sandbox

import java.io.File

/** Which files an extension may ask the app to open with the program Windows associates with them (opening runs programs, scripts and shortcuts) */
object SafeOpen {
    private val passive = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "m3u8", "m3u", "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac",
        "srt", "vtt", "ass", "ssa", "sub", "txt", "pdf", "jpg", "jpeg", "png", "gif", "webp", "bmp", "json",
    )

    /** A file whose opening shows or plays something and starts nothing */
    fun isPassive(file: File): Boolean = file.extension.lowercase() in passive
}

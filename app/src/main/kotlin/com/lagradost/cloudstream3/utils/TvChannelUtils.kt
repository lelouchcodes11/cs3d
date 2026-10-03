package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.SearchResponse

// desktop: no Android TV home screen channels on desktop
object TvChannelUtils {
    fun Context.saveProgramId(programId: Long) {}
    fun Context.getStoredProgramIds(): List<Long> = emptyList()
    fun Context.removeProgramId(programId: Long) {}
    fun getChannelId(context: Context, channelName: String): Long? = 1L
    fun updatePrograms(context: Context, channelId: Long, list: List<SearchResponse>) {}
    fun deleteChannel(context: Context, channelId: Long) {}
    fun createChannel(context: Context, channelName: String, channelDescription: String): Long? = null
    fun createTvChannel(context: Context) {}
    fun addPrograms(context: Context, channelId: Long, items: List<Any>) {}
    fun deleteStoredPrograms(context: Context) {}
}

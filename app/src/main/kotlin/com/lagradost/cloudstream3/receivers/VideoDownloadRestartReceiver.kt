package com.lagradost.cloudstream3.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager

class VideoDownloadRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Broadcast Listened", "Service tried to stop")
        context?.let { DownloadQueueManager.init(it) }
    }
}

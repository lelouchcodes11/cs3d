package com.lagradost.desktop.net

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path

/**
 * The file system of the HTTP cache. When a response has been read to its end, the cache moves its finished file over the entry's file; on Windows
 * that fails ("AccessDeniedException ... .0.tmp -> ...") while another request for the same address is reading that entry (the extension
 * repository is asked by several screens at start-up), and the failure ended the request that had just been read in full: the repository was
 * "unreachable" for that check. The reader of such a small entry is done within milliseconds, so the move is tried again a few times.
 * (Leaving the move out is not safe: an entry is two files, headers and body, and they could then come from different answers.)
 */
class CacheFileSystem(delegate: FileSystem = SYSTEM) : ForwardingFileSystem(delegate) {
    override fun atomicMove(source: Path, target: Path) {
        var attempt = 0
        while (true) {
            try {
                super.atomicMove(source, target)
                return
            } catch (e: IOException) {
                if (++attempt > 5) throw e
                Thread.sleep(10L * attempt)
            }
        }
    }
}

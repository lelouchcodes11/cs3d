package android.content.pm

import java.io.InputStream
import java.io.OutputStream

class PackageInstaller {
    class SessionParams(val mode: Int) {
        companion object {
            const val MODE_FULL_INSTALL = 1
            const val USER_ACTION_NOT_REQUIRED = 2
        }

        fun setRequireUserAction(action: Int) {}
        fun setSize(size: Long) {}
    }

    class Session : java.io.Closeable {
        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream =
            OutputStream.nullOutputStream()

        fun fsync(out: OutputStream) {}
        fun commit(statusReceiver: android.content.IntentSender) {}
        fun abandon() {}
        override fun close() {}
    }

    companion object {
        const val EXTRA_STATUS = "android.content.pm.extra.STATUS"
        const val EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE"
        const val STATUS_PENDING_USER_ACTION = -1
        const val STATUS_SUCCESS = 0
        const val STATUS_FAILURE = 1
    }

    fun createSession(params: SessionParams): Int = 0
    fun openSession(sessionId: Int): Session = Session()
    fun abandonSession(sessionId: Int) {}
}

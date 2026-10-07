package com.lagradost.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File

interface Mpv : Library {
    companion object {
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_OSD_STRING = 2
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5
        const val MPV_FORMAT_NODE = 6
        const val MPV_FORMAT_NODE_ARRAY = 7
        const val MPV_FORMAT_NODE_MAP = 8
        const val MPV_FORMAT_BYTE_ARRAY = 9

        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY = 4
        const val MPV_EVENT_COMMAND_REPLY = 5
        const val MPV_EVENT_START_FILE = 6
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_IDLE = 11
        const val MPV_EVENT_TICK = 14
        const val MPV_EVENT_CLIENT_MESSAGE = 16
        const val MPV_EVENT_VIDEO_RECONFIG = 17
        const val MPV_EVENT_AUDIO_RECONFIG = 18
        const val MPV_EVENT_SEEK = 20
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_PROPERTY_CHANGE = 22
        const val MPV_EVENT_QUEUE_OVERFLOW = 24
        const val MPV_EVENT_HOOK = 25

        const val MPV_END_FILE_REASON_EOF = 0
        const val MPV_END_FILE_REASON_STOP = 2
        const val MPV_END_FILE_REASON_QUIT = 3
        const val MPV_END_FILE_REASON_ERROR = 4
        const val MPV_END_FILE_REASON_REDIRECT = 5

        // render.h: mpv_render_param_type and update flags
        const val MPV_RENDER_PARAM_INVALID = 0
        const val MPV_RENDER_PARAM_API_TYPE = 1
        const val MPV_RENDER_PARAM_NEXT_FRAME_INFO = 11
        const val MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME = 12
        const val MPV_RENDER_PARAM_SW_SIZE = 17
        const val MPV_RENDER_PARAM_SW_FORMAT = 18
        const val MPV_RENDER_PARAM_SW_STRIDE = 19
        const val MPV_RENDER_PARAM_SW_POINTER = 20
        const val MPV_RENDER_UPDATE_FRAME = 1L

        /**
         * The folder of mpv-2.dll: next to the app (installer, portable folder, development run). Older versions unpacked the 115 MB DLL from
         * their jar into `<data>/cache/natives`; that copy is deleted.
         */
        private fun nativesDir(): File? {
            val dir = com.lagradost.desktop.runtime.web.NativeFiles.dirOf("mpv-2.dll") ?: return null
            runCatching { File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "cache/natives").deleteRecursively() }
            return dir
        }

        val INSTANCE: Mpv by lazy {
            runCatching { nativesDir() }.getOrNull()?.let { dir ->
                val current = System.getProperty("jna.library.path") ?: ""
                System.setProperty("jna.library.path", if (current.isEmpty()) dir.absolutePath else "${dir.absolutePath};$current")
            }
            try {
                Native.load("mpv-2", Mpv::class.java)
            } catch (t: Throwable) {
                try {
                    Native.load("libmpv-2", Mpv::class.java)
                } catch (t2: Throwable) {
                    Native.load("mpv", Mpv::class.java)
                }
            }
        }
    }

    @Structure.FieldOrder("name", "format", "data")
    open class MpvEventProperty : Structure {
        @JvmField var name: String? = null
        @JvmField var format: Int = 0
        @JvmField var data: Pointer? = null

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }
    }

    @Structure.FieldOrder("prefix", "level", "text", "log_level")
    open class MpvEventLogMessage : Structure {
        @JvmField var prefix: String? = null
        @JvmField var level: String? = null
        @JvmField var text: String? = null
        @JvmField var log_level: Int = 0

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }
    }

    @Structure.FieldOrder("reason", "error", "playlist_entry_id")
    open class MpvEventEndFile : Structure {
        @JvmField var reason: Int = 0
        @JvmField var error: Int = 0
        @JvmField var playlist_entry_id: Long = 0L

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }
    }

    @Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
    open class MpvEvent : Structure {
        @JvmField var event_id: Int = 0
        @JvmField var error: Int = 0
        @JvmField var reply_userdata: Long = 0L
        @JvmField var data: Pointer? = null

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }

        fun getProperty(): MpvEventProperty? = data?.let { MpvEventProperty(it) }
        fun getLogMessage(): MpvEventLogMessage? = data?.let { MpvEventLogMessage(it) }
        fun getEndFile(): MpvEventEndFile? = data?.let { MpvEventEndFile(it) }
    }

    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_destroy(ctx: Pointer)
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_command_async(ctx: Pointer, reply_userdata: Long, args: Array<String?>): Int
    fun mpv_set_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_get_property_osd_string(ctx: Pointer, name: String): Pointer?
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_observe_property(mpv: Pointer, reply_userdata: Long, name: String, format: Int): Int
    fun mpv_unobserve_property(mpv: Pointer, registered_reply_userdata: Long): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_request_log_messages(ctx: Pointer, min_level: String): Int
    fun mpv_free(data: Pointer?)
    fun mpv_error_string(error: Int): String?
    fun mpv_event_name(event: Int): String?

    /** render.h update callback: called from mpv threads, must not call mpv functions */
    fun interface RenderUpdateCallback : com.sun.jna.Callback {
        fun invoke(cbCtx: Pointer?)
    }

    fun mpv_render_context_create(res: com.sun.jna.ptr.PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: RenderUpdateCallback?, callbackCtx: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    /** mpv_render_param passed by value: {int type; void* data} */
    @Structure.FieldOrder("type", "data")
    open class RenderParam() : Structure(), Structure.ByValue {
        @JvmField var type: Int = 0
        @JvmField var data: Pointer? = null
    }

    /** Fills the mpv_render_frame_info that [param] points to (MPV_RENDER_PARAM_NEXT_FRAME_INFO) for the frame the next render call draws */
    fun mpv_render_context_get_info(ctx: Pointer, param: RenderParam): Int

    /** mpv's clock in microseconds (the clock of the target times that mpv_render_frame_info carries) */
    fun mpv_get_time_us(ctx: Pointer): Long
}

package com.lagradost.desktop.platform

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Single-instance local loopback socket IPC for CloudStream Desktop.
 * As per WP10.2:
 * - On startup, tries to connect to 127.0.0.1:<fixed port>.
 * - If a running instance answers, forwards command-line args / URLs and exits.
 * - Otherwise binds the port and listens for deep links / URLs from subsequent launches,
 *   delivering them to MainActivity.onNewIntent.
 */
object SingleInstanceIpc {
    private const val TAG = "SingleInstanceIpc"
    private const val DEFAULT_PORT = 52525
    private const val IPC_MAGIC = "CS_IPC_ARGS2"

    private val port: Int
        get() = System.getProperty("cloudstream.ipcport")?.toIntOrNull() ?: DEFAULT_PORT

    /**
     * The port only answers a sender that knows the secret of this run, kept in a file of the data folder (a program that cannot read the user's
     * data folder, such as another Windows user's or a sandboxed helper, gets "ERR" and nothing it sends is acted on).
     */
    private fun tokenFile(port: Int): java.io.File =
        java.io.File(System.getProperty("cloudstream.data")?.let { java.io.File(it) } ?: com.lagradost.desktop.runtime.AndroidRuntime.defaultDataDir(), "ipc-$port.token")

    private fun newToken(port: Int): String {
        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        runCatching { tokenFile(port).also { it.parentFile?.mkdirs() }.writeText(token) }.onFailure { Log.w(TAG, "could not write the IPC token: $it") }
        return token
    }

    /** A line of at most [max] characters, or null (end of stream, or too long: the sender is not the app) */
    private fun readLineBounded(reader: BufferedReader, max: Int): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == 10) return sb.toString().trimEnd(13.toChar())
            if (sb.length >= max) return null
            sb.append(c.toChar())
        }
    }

    private fun sameToken(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    @Volatile
    private var serverSocket: ServerSocket? = null

    /** Does anything answer on [port]? (a connection that sends nothing) */
    fun isRunning(port: Int = this.port): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 400); true }
    } catch (_: Throwable) {
        false
    }

    /**
     * Tries to send arguments to an already-running primary instance.
     * Returns true if successfully delivered, false if no running instance answered.
     */
    fun sendArgsToExistingInstance(args: Array<String>): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1000)
                socket.soTimeout = 3000
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val token = runCatching { tokenFile(port).readText().trim() }.getOrDefault("")
                writer.println("$IPC_MAGIC $token")
                for (arg in args) {
                    writer.println(arg)
                }
                writer.println() // empty line marks end of message

                val response = reader.readLine()
                response == "OK"
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Starts the loopback IPC server daemon for this primary instance.
     */
    fun startServer(onArgsReceived: (List<String>) -> Unit) {
        try {
            val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            val token = newToken(port)

            thread(name = "cloudstream-ipc-server", isDaemon = true) {
                while (!server.isClosed) {
                    try {
                        val client = server.accept()
                        thread(name = "cloudstream-ipc-client", isDaemon = true) {
                            try {
                                client.use { sock ->
                                    sock.soTimeout = 5000
                                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                                    val writer = PrintWriter(sock.getOutputStream(), true)

                                    val magic = readLineBounded(reader, 128)
                                    if (magic != null && sameToken(magic, "$IPC_MAGIC $token")) {
                                        val args = mutableListOf<String>()
                                        var line: String? = readLineBounded(reader, 8192)
                                        // a message is a handful of short lines: anything longer is not from the app
                                        while (line != null && line.isNotEmpty() && args.size < 64) {
                                            args.add(line)
                                            line = readLineBounded(reader, 8192)
                                        }
                                        writer.println("OK")
                                        if (args.isNotEmpty()) {
                                            onArgsReceived(args)
                                        }
                                    } else {
                                        writer.println("ERR")
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "Error handling IPC client connection: $t")
                            }
                        }
                    } catch (t: Throwable) {
                        if (!server.isClosed) {
                            Log.w(TAG, "IPC accept error: $t")
                        }
                    }
                }
            }
            Log.i(TAG, "IPC server listening on 127.0.0.1:$port")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start IPC server on port $port: $t")
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        } finally {
            serverSocket = null
        }
    }
}

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
    private const val IPC_MAGIC = "CS_IPC_ARGS"

    private val port: Int
        get() = System.getProperty("cloudstream.ipcport")?.toIntOrNull() ?: DEFAULT_PORT

    @Volatile
    private var serverSocket: ServerSocket? = null

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

                writer.println(IPC_MAGIC)
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

                                    val magic = reader.readLine()
                                    if (magic == IPC_MAGIC) {
                                        val args = mutableListOf<String>()
                                        var line: String? = reader.readLine()
                                        while (line != null && line.isNotEmpty()) {
                                            args.add(line)
                                            line = reader.readLine()
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

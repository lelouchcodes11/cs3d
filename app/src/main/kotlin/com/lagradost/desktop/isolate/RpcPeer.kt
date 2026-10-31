package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Two programs that talk over a pair of pipes: one JSON message per line. A message is a request (`q`: an operation and a body that the other side
 * answers), a response (`r`, with the same number) or an event (`e`: something that belongs to a request that is still open, e.g. one link of
 * `loadLinks`). Requests go both ways: the app asks the sandbox to search, the sandbox asks the app to open a hidden browser.
 *
 * What comes from the sandbox is data of an untrusted program: lines are bounded, and every operation the app serves is checked by its handler.
 */
class RpcPeer(
    private val label: String,
    private val input: InputStream,
    private val output: OutputStream,
    private val maxLine: Int = 32 * 1024 * 1024,
    private val serve: suspend (Incoming) -> JsonNode?,
) {
    /** A request the other side made; [emit] sends an event that belongs to it */
    inner class Incoming(val id: Long, val op: String, val body: JsonNode) {
        fun emit(op: String, body: Any?) = send("e", id, op, body = body)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, Call>()
    private val running = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val closed = AtomicBoolean()
    private val writeLock = Any()

    private class Call(val done: CompletableDeferred<JsonNode>, val onEvent: ((String, JsonNode) -> Unit)?)

    val isClosed: Boolean get() = closed.get()

    /** Told once when the pipe ends (the other program ended or sent something that is not a message) */
    @Volatile var onClosed: ((String) -> Unit)? = null

    fun start() {
        Thread({ readLoop() }, "rpc-$label").apply { isDaemon = true }.start()
    }

    private fun readLoop() {
        var reason = "the other side closed the pipe"
        try {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                var from = 0
                for (i in 0 until n) {
                    if (chunk[i] == '\n'.code.toByte()) {
                        buffer.write(chunk, from, i - from)
                        handleLine(buffer.toByteArray())
                        buffer.reset()
                        from = i + 1
                    }
                }
                buffer.write(chunk, from, n - from)
                if (buffer.size() > maxLine) { reason = "a message is longer than $maxLine bytes"; break }
            }
        } catch (t: Throwable) {
            reason = t.toString()
        }
        close(reason)
    }

    private fun handleLine(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val node = runCatching { mapper.readTree(bytes) }.getOrNull() as? ObjectNode ?: return
        val kind = node.get("k")?.asText() ?: return
        val id = node.get("i")?.asLong() ?: return
        when (kind) {
            "q" -> {
                val incoming = Incoming(id, node.get("o")?.asText() ?: "", node.get("b") ?: JsonNodeFactory.instance.nullNode())
                val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        val result = serve(incoming)
                        send("r", id, null, ok = true, body = result)
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        send("r", id, null, ok = false, error = (t.message ?: t.javaClass.simpleName).take(500), failure = t)
                    }
                }
                running[id] = job
                job.invokeOnCompletion { running.remove(id) }
                job.start()
            }
            // the other side no longer waits for this request (its caller was cancelled or ran out of time)
            "x" -> {
                running[id]?.cancel()
            }
            "r" -> pending.remove(id)?.let { call ->
                if (node.get("ok")?.asBoolean() == true) call.done.complete(node.get("b") ?: JsonNodeFactory.instance.nullNode())
                else call.done.completeExceptionally(remoteFailure(node))
            }
            "e" -> pending[id]?.onEvent?.let { handler ->
                runCatching { handler(node.get("o")?.asText() ?: "", node.get("b") ?: JsonNodeFactory.instance.nullNode()) }
                    .onFailure { Log.w("RpcPeer", "$label: event handler failed: $it") }
            }
        }
    }

    /**
     * What the app needs to treat a failure of an extension in the sandbox as it treats one in the app (the error of a search page: "This operation is not implemented",
     * "Error loading, try again later", the line of the extension that failed): the kind of failure and the lines of its stack that name a file, not the pipe's own frames
     */
    private fun describe(t: Throwable, node: ObjectNode) {
        val kind = when (t) {
            is NotImplementedError -> "nie"
            is com.lagradost.cloudstream3.ErrorLoadingException -> "ele"
            is NullPointerException -> "npe"
            else -> null
        }
        if (kind != null) node.put("c", kind)
        val frames = t.stackTrace.asSequence()
            .filter { it.fileName != null && it.lineNumber > 0 && !it.className.startsWith("java.") && !it.className.startsWith("jdk.") && !it.className.startsWith("kotlin") }
            .take(12).map { it.fileName + ":" + it.lineNumber }.toList()
        if (frames.isNotEmpty()) node.set<JsonNode>("s", mapper.createArrayNode().also { a -> frames.forEach { a.add(it) } })
    }

    private fun remoteFailure(node: JsonNode): Throwable {
        val message = node.get("m")?.asText() ?: "failed"
        val failure: Throwable = when (node.get("c")?.asText()) {
            "nie" -> NotImplementedError(message)
            "ele" -> com.lagradost.cloudstream3.ErrorLoadingException(message)
            "npe" -> NullPointerException(message)
            else -> IllegalStateException(message)
        }
        val frames = node.get("s")?.takeIf { it.isArray }?.mapNotNull { f ->
            val text = f.asText()
            val at = text.lastIndexOf(':')
            val line = if (at > 0) text.substring(at + 1).toIntOrNull() else null
            if (line != null) StackTraceElement("sandbox", "frame", text.substring(0, at).take(120), line) else null
        }.orEmpty()
        // the frames of the extension, as they were in the sandbox, so the message names the same lines as it does in the app
        if (frames.isNotEmpty()) failure.stackTrace = frames.toTypedArray()
        return failure
    }

    private fun send(kind: String, id: Long, op: String?, ok: Boolean? = null, error: String? = null, body: Any? = null, failure: Throwable? = null) {
        if (closed.get()) return
        val node = mapper.createObjectNode().put("k", kind).put("i", id)
        if (op != null) node.put("o", op)
        if (ok != null) node.put("ok", ok)
        if (error != null) node.put("m", error)
        if (failure != null) describe(failure, node)
        if (body != null) node.set<JsonNode>("b", if (body is JsonNode) body else mapper.valueToTree(body))
        val line = (mapper.writeValueAsString(node) + "\n").toByteArray(Charsets.UTF_8)
        try {
            synchronized(writeLock) { output.write(line); output.flush() }
        } catch (t: Throwable) {
            close("write failed: $t")
        }
    }

    /** Asks the other side to do [op]; [onEvent] gets the events that belong to this request. Throws when it fails, ends or takes longer than [timeoutMs]. */
    suspend fun call(op: String, body: Any? = null, timeoutMs: Long = 60_000, onEvent: ((String, JsonNode) -> Unit)? = null): JsonNode {
        if (closed.get()) throw IllegalStateException("the sandbox is not running")
        val id = ids.incrementAndGet()
        val call = Call(CompletableDeferred(), onEvent)
        pending[id] = call
        send("q", id, op, body = body)
        try {
            return withTimeout(timeoutMs) { call.done.await() }
        } catch (t: kotlinx.coroutines.CancellationException) {
            send("x", id, null)
            throw t
        } finally {
            pending.remove(id)
        }
    }

    fun close(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        pending.values.forEach { it.done.completeExceptionally(IllegalStateException("the sandbox ended: $reason")) }
        pending.clear()
        runCatching { input.close() }
        runCatching { output.close() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        onClosed?.invoke(reason)
    }
}

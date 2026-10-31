package com.lagradost.desktop.isolate

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.throwAbleToResource
import kotlinx.coroutines.runBlocking
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A failure of an extension in the sandbox reaches the app as the same kind of failure, so the app words it as it words one of an extension in the app */
class RpcPeerTest {
    private val peers = mutableListOf<RpcPeer>()

    private fun connect(serve: suspend (RpcPeer.Incoming) -> JsonNode?): RpcPeer {
        val toServer = PipedOutputStream()
        val serverIn = PipedInputStream(toServer, 1 shl 16)
        val toCaller = PipedOutputStream()
        val callerIn = PipedInputStream(toCaller, 1 shl 16)
        val caller = RpcPeer("caller", callerIn, toServer) { null }
        val server = RpcPeer("server", serverIn, toCaller, serve = serve)
        peers += caller; peers += server
        caller.start(); server.start()
        return caller
    }

    @AfterTest
    fun tearDown() = peers.forEach { it.close("test over") }

    @Test
    fun aNotImplementedExtensionFailsAsOneInTheApp() = runBlocking {
        val caller = connect { throw NotImplementedError("An operation is not implemented.") }
        val failure = assertFailsWith<NotImplementedError> { caller.call("search", null, 10_000) }
        val resource = throwAbleToResource<Unit>(failure)
        assertTrue(resource is Resource.Failure)
        assertEquals("This operation is not implemented.", resource.errorString)
    }

    @Test
    fun anErrorTheExtensionNamesStaysARetryableOne() = runBlocking {
        val caller = connect { throw ErrorLoadingException("The site is down") }
        val failure = assertFailsWith<ErrorLoadingException> { caller.call("load", null, 10_000) }
        val resource = throwAbleToResource<Unit>(failure)
        assertTrue(resource is Resource.Failure)
        assertTrue(resource.isNetworkError, "the app offers to try again")
        assertEquals("The site is down", resource.errorString)
    }

    @Test
    fun aNullPointerNamesTheLineOfTheExtension() = runBlocking {
        val caller = connect {
            val npe = NullPointerException("x")
            npe.stackTrace = arrayOf(StackTraceElement("com.example.ExampleProvider", "search", "ExampleProvider.kt", 42))
            throw npe
        }
        val failure = assertFailsWith<NullPointerException> { caller.call("search", null, 10_000) }
        val resource = throwAbleToResource<Unit>(failure)
        assertTrue(resource is Resource.Failure)
        assertTrue(resource.errorString.contains("ExampleProvider.kt 42") || resource.errorString.contains("ExampleProvider.kt:42"), "the message names the line: ${resource.errorString}")
    }

    @Test
    fun anyOtherFailureStaysAPlainOne() = runBlocking {
        val caller = connect { throw IllegalArgumentException("bad input") }
        val failure = assertFailsWith<IllegalStateException> { caller.call("search", null, 10_000) }
        assertEquals("bad input", failure.message)
    }
}

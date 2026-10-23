package com.lagradost.desktop.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The answers AniList and MyAnimeList really gave on 2026-10-21, and the ones that must not count as an outage */
class ServiceOutageTest {
    @Test
    fun aniListSwitchedOff() {
        val body = """{"errors":[{"message":"The AniList API has been temporarily disabled due to severe stability issues.","status":403,"locations":[{"line":1,"column":1}]}],"data":null}"""
        val why = ServiceOutage.reason("AniList", 403, body)
        assertEquals("AniList says: The AniList API has been temporarily disabled due to severe stability issues.", why)
    }

    @Test
    fun malInMaintenance() {
        val body = """{"error":"maintenance","message":"MyAnimeList is temporarily in maintenance mode. Service will be resumed as soon as possible."}"""
        val why = ServiceOutage.reason("MAL", 503, body)
        assertNotNull(why)
        assertTrue(why.startsWith("MAL says: MyAnimeList is temporarily in maintenance mode"))
    }

    @Test
    fun aWebPageWhereDataWasAskedFor() {
        assertNotNull(ServiceOutage.reason("MAL", 503, "<!doctype html><html lang=\"en\"><head><title>503</title>"))
        // a page with a good status is not an outage (a sign-up page opened through the same host)
        assertNull(ServiceOutage.reason("MAL", 200, "<!doctype html><html lang=\"en\"><head><title>Home</title>"))
    }

    @Test
    fun aRefusedSignInSaysSignInAgain() {
        assertTrue(ServiceOutage.reason("MAL", 401, """{"error":"invalid_token","message":"The access token is invalid"}""")!!.contains("Sign in again"))
        assertTrue(ServiceOutage.reason("AniList", 400, """{"errors":[{"message":"Invalid token","status":400}],"data":null}""")!!.contains("Sign in again"))
    }

    @Test
    fun normalAnswersAndNotFoundAreNoOutage() {
        assertNull(ServiceOutage.reason("AniList", 200, """{"data":{"Media":{"id":20}}}"""))
        // a title that does not exist
        assertNull(ServiceOutage.reason("AniList", 404, """{"errors":[{"message":"Not Found.","status":404}],"data":null}"""))
        assertNull(ServiceOutage.reason("MAL", 404, """{"error":"not_found","message":""}"""))
        // some of the data with an error beside it is still data
        assertNull(ServiceOutage.reason("AniList", 200, """{"errors":[{"message":"x","status":400}],"data":{"Media":{"id":1}}}"""))
        assertNull(ServiceOutage.reason("MAL", 200, """{"data":[],"paging":{}}"""))
        assertNull(ServiceOutage.reason("Kitsu", 200, ""))
    }

    @Test
    fun requireDataThrowsTheServicesOwnWords() {
        val json = org.json.JSONObject("""{"errors":[{"message":"Down for now","status":503}],"data":null}""")
        val e = runCatching { ServiceOutage.requireData("AniList", json) }.exceptionOrNull()
        assertNotNull(e)
        assertEquals("AniList says: Down for now", e.message)
        // an answer with data passes
        ServiceOutage.requireData("AniList", org.json.JSONObject("""{"data":{"Page":{}}}"""))
    }
}

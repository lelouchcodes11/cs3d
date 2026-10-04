package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The answers of the two subtitle services to a sign-in, in the shapes they really have (checked against the live services) */
class SubtitleLoginAnswersTest {

    @Test
    fun subdlWrongDetailsGiveTheReasonNotAParserError() {
        // HTTP 200 with status false: this used to be parsed as a login and failed with "missing value for creator parameter token"
        val (token, reason) = SubDlApi.readLoginAnswer("""{"status":false,"error":"Email or password is not valid."}""", "token")
        assertNull(token)
        assertEquals("Email or password is not valid.", reason)
    }

    @Test
    fun subdlLoginWithoutAnyOfTheAccountFieldsStillGivesTheToken() {
        // userData used to demand country, scStepCode, scVerified ... which the app never uses
        val (token, _) = SubDlApi.readLoginAnswer("""{"status":true,"token":"abc.def.ghi","userData":{"email":"a@b.c"}}""", "token")
        assertEquals("abc.def.ghi", token)
        assertEquals("k3y", SubDlApi.readLoginAnswer("""{"ok":true,"api_key":"k3y","usage":{"total":3}}""", "api_key").first)
    }

    @Test
    fun subdlAnswerThatIsNotJsonIsNotATokenAndNotACrash() {
        assertEquals(null to null, SubDlApi.readLoginAnswer("<html>blocked</html>", "token"))
        assertEquals(null to null, SubDlApi.readLoginAnswer("", "token"))
        assertEquals(null to null, SubDlApi.readLoginAnswer("[1,2]", "token"))
        // a null token is not a token
        assertNull(SubDlApi.readLoginAnswer("""{"token":null}""", "token").first)
    }

    @Test
    fun subdlRejectedApiKeyIsRecognisedByItsReason() {
        val (_, reason) = SubDlApi.readLoginAnswer("""{"status":false,"statusCode":403,"error":"not_authorized","message":"Not Authorized"}""", "")
        assertEquals("not_authorized", reason)
        assertTrue(reason!!.contains("authorized", ignoreCase = true))
    }

    @Test
    fun openSubtitlesWrongDetailsExplainTheUserName() {
        val (token, reason) = OpenSubtitlesApi.readLoginAnswer("""{"message":"Error, invalid username/password  ","status":401}""")
        assertNull(token)
        val text = OpenSubtitlesApi.loginFailure(401, reason)
        assertTrue(text.contains("invalid username/password"), text)
        assertTrue(text.contains("user name, not your e-mail"), text)
    }

    @Test
    fun openSubtitlesSuccessGivesTheToken() {
        val (token, reason) = OpenSubtitlesApi.readLoginAnswer("""{"user":{"allowed_downloads":20},"base_url":"api.opensubtitles.com","token":"jwt.jwt.jwt","status":200}""")
        assertEquals("jwt.jwt.jwt", token)
        assertNull(reason)
    }

    @Test
    fun openSubtitlesRateLimitAndUnknownAnswers() {
        assertTrue(OpenSubtitlesApi.loginFailure(429, null).contains("too many"))
        assertTrue(OpenSubtitlesApi.loginFailure(500, null).contains("HTTP 500"))
        assertEquals(null to null, OpenSubtitlesApi.readLoginAnswer("not json"))
    }
}

package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClearKeyJwkTest {
    private val kidHex = "c94a85da7e44563f8f3a687b5bef641c"
    private val keyHex = "445450834250887830adbde7c75caa2b"
    private val kid2Hex = "0123456789abcdef0123456789abcdef"
    private val key2Hex = "fedcba9876543210fedcba9876543210"
    private fun bytes(hex: String) = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun b64url(hex: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(hex))
    private fun b64std(hex: String) = java.util.Base64.getEncoder().encodeToString(bytes(hex))
    private fun uuid(hex: String) = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"

    @Test
    fun hexIsLowerCased() {
        assertEquals(kidHex, MpvPlayer.clearKeyHex(kidHex.uppercase()))
        assertEquals(kidHex, MpvPlayer.clearKeyHex("  $kidHex  "))
    }

    @Test
    fun base64UrlAndStandardBase64GiveTheSameHex() {
        assertEquals(kidHex, MpvPlayer.clearKeyHex(b64url(kidHex)))
        assertEquals(kidHex, MpvPlayer.clearKeyHex(b64std(kidHex)))
        // bytes whose base64 has '+' and '/' (standard) or '-' and '_' (base64url)
        val odd = "fbffbffbffbffbffbffbffbffbffbfff"
        assertTrue(b64std(odd).contains('+') && b64url(odd).contains('-'))
        assertEquals(odd, MpvPlayer.clearKeyHex(b64std(odd)))
        assertEquals(odd, MpvPlayer.clearKeyHex(b64url(odd)))
    }

    @Test
    fun aUuidKidIsReadAsHex() {
        assertEquals(kidHex, MpvPlayer.clearKeyHex(uuid(kidHex)))
        assertEquals(kidHex, MpvPlayer.clearKeyHex(uuid(kidHex).uppercase()))
    }

    @Test
    fun wrongLengthsAreRefused() {
        assertNull(MpvPlayer.clearKeyHex(kidHex.dropLast(1)))
        assertNull(MpvPlayer.clearKeyHex(kidHex + "0"))
        assertNull(MpvPlayer.clearKeyHex(""))
        assertNull(MpvPlayer.clearKeyHex("not a key at all"))
    }

    @Test
    fun aJsonKeySetGivesEveryPairByKid() {
        val json = """{"keys":[{"kty":"oct","k":"${b64url(keyHex)}","kid":"${b64url(kidHex)}"},{"kty":"oct","kid":"${b64url(kid2Hex)}","k":"${b64url(key2Hex)}"}],"type":"temporary"}"""
        assertEquals(listOf(kidHex to keyHex, kid2Hex to key2Hex), MpvPlayer.jwkPairs(json))
    }

    @Test
    fun aKeySetWithUuidKidsIsRead() {
        val json = """[{"kid":"${uuid(kidHex)}","k":"$keyHex"}]"""
        assertEquals(listOf(kidHex to keyHex), MpvPlayer.jwkPairs(json))
    }

    @Test
    fun theMiddleOfAKeySetIsRead() {
        // what follows an opening the extension's template had already written: {"keys":[{"k":"
        val fragment = """${b64url(keyHex)}","kid":"${b64url(kidHex)}"},{"kid":"${b64url(kid2Hex)}","k":"${b64url(key2Hex)}"}]}"""
        assertEquals(listOf(kidHex to keyHex, kid2Hex to key2Hex), MpvPlayer.jwkPairs(fragment))
    }

    @Test
    fun aKeySetInTheTemplateKeepsItsLastKeyWithTheLinksKid() {
        // JIO TV: the "key" fills the phone's template {"keys":[{"kty":"oct","k":"<key>","kid":"<kid>"}]}; its last key belongs to the link's KID
        val fragment = """${b64url(keyHex)}","kid":"${b64url(kidHex)}"},{"kty":"oct","k":"${b64url(key2Hex)}"""
        assertEquals(listOf(kidHex to keyHex, kid2Hex to key2Hex), MpvPlayer.clearKeyPairs(fragment, b64url(kid2Hex), null))
        // a plain key or a complete set reads as before
        assertTrue(MpvPlayer.clearKeyPairs(keyHex, kidHex, null).isEmpty())
        val json = """{"keys":[{"kty":"oct","k":"$keyHex","kid":"$kidHex"}]}"""
        assertEquals(listOf(kidHex to keyHex), MpvPlayer.clearKeyPairs(json, kid2Hex, null))
    }

    @Test
    fun aPlainKeyIsNotAKeySet() {
        assertTrue(MpvPlayer.jwkPairs(keyHex).isEmpty())
        assertTrue(MpvPlayer.jwkPairs(b64url(keyHex)).isEmpty())
        assertTrue(MpvPlayer.jwkPairs("{}").isEmpty())
    }
}

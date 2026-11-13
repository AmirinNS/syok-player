package io.github.amirinns.syok.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationUrlTest {

    @Test
    fun validHttpsStreamUrls_passValidation() {
        val mp3Url = "https://stream.example.com/live.mp3"
        val aacUrl = "https://stream.radioparadise.com/aac-128"
        val streamWithoutExtension = "https://stream-relay-geo.ntslive.net/stream"

        assertTrue(StationValidator.validateUrl(mp3Url) is StationValidator.Result.Valid)
        assertTrue(StationValidator.validateUrl(aacUrl) is StationValidator.Result.Valid)
        assertTrue(StationValidator.validateUrl(streamWithoutExtension) is StationValidator.Result.Valid)
    }

    @Test
    fun validHlsUrl_passesValidation() {
        val hlsUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        assertTrue(StationValidator.validateUrl(hlsUrl) is StationValidator.Result.Valid)
    }

    @Test
    fun invalidSchemes_rejected() {
        val httpUrl = "http://icecast.example.com/stream"
        val ftpUrl = "ftp://ftp.example.com/stream.mp3"
        val fileUrl = "file:///storage/emulated/0/stream.mp3"
        val noScheme = "stream.example.com/live"

        assertTrue(StationValidator.validateUrl(httpUrl) is StationValidator.Result.Invalid)
        assertTrue(StationValidator.validateUrl(ftpUrl) is StationValidator.Result.Invalid)
        assertTrue(StationValidator.validateUrl(fileUrl) is StationValidator.Result.Invalid)
        assertTrue(StationValidator.validateUrl(noScheme) is StationValidator.Result.Invalid)
    }

    @Test
    fun urlWithUserInfoOrCredentials_rejected() {
        val userPass = "https://user:password@stream.example.com/radio"
        val userOnly = "https://admin@stream.example.com/radio"

        val res1 = StationValidator.validateUrl(userPass)
        val res2 = StationValidator.validateUrl(userOnly)

        assertTrue(res1 is StationValidator.Result.Invalid)
        assertTrue(res2 is StationValidator.Result.Invalid)
    }

    @Test
    fun blankOrMissingNames_rejected() {
        assertTrue(StationValidator.validateName("") is StationValidator.Result.Invalid)
        assertTrue(StationValidator.validateName("   ") is StationValidator.Result.Invalid)
        assertTrue(StationValidator.validateName("\t\n") is StationValidator.Result.Invalid)
    }

    @Test
    fun overLongNames_rejected() {
        val longName = "A".repeat(StationValidator.MAX_NAME_LENGTH + 1)
        assertTrue(StationValidator.validateName(longName) is StationValidator.Result.Invalid)

        val exactMaxName = "A".repeat(StationValidator.MAX_NAME_LENGTH)
        assertTrue(StationValidator.validateName(exactMaxName) is StationValidator.Result.Valid)
    }

    @Test
    fun overLongUrls_rejected() {
        val longUrl = "https://stream.example.com/" + "a".repeat(StationValidator.MAX_URL_LENGTH)
        assertTrue(StationValidator.validateUrl(longUrl) is StationValidator.Result.Invalid)
    }

    @Test
    fun playlistFilesM3uAndPls_rejected() {
        val m3u = "https://stream.example.com/listen.m3u"
        val pls = "https://stream.example.com/listen.pls"

        val resM3u = StationValidator.validateUrl(m3u)
        val resPls = StationValidator.validateUrl(pls)

        assertTrue(resM3u is StationValidator.Result.Invalid)
        assertTrue(resPls is StationValidator.Result.Invalid)
    }

    @Test
    fun atSignOutsideAuthority_allowed() {
        val url = "https://stream.example.com/live?tag=@night"
        assertTrue(StationValidator.validateUrl(url) is StationValidator.Result.Valid)
    }

    @Test
    fun combinedValidator_checksNameAndUrl() {
        val validRes = StationValidator.validate("Radio Paradise", "https://stream.radioparadise.com/aac-128")
        assertTrue(validRes is StationValidator.Result.Valid)

        val blankNameRes = StationValidator.validate("  ", "https://stream.radioparadise.com/aac-128")
        assertTrue(blankNameRes is StationValidator.Result.Invalid)

        val badUrlRes = StationValidator.validate("Radio Paradise", "http://stream.radioparadise.com/aac-128")
        assertTrue(badUrlRes is StationValidator.Result.Invalid)
    }
}

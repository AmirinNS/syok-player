package io.github.amirinns.syok.radio

import java.net.URI

/**
 * Validates internet radio station names and URLs.
 * Enforces:
 * - Non-blank names up to [MAX_NAME_LENGTH] characters.
 * - Non-blank URLs up to [MAX_URL_LENGTH] characters.
 * - Strict HTTPS scheme requirement (no HTTP or other schemes).
 * - Prohibition of embedded user-info / credentials in URLs.
 * - Rejection of playlist files (.m3u, .pls); only direct streams and HLS (.m3u8) are supported.
 * HTTPS-to-HTTP redirects are blocked in PlayerFactory (allowCrossProtocolRedirects = false).
 */
object StationValidator {

    const val MAX_NAME_LENGTH = 100
    const val MAX_URL_LENGTH = 2048

    sealed class Result {
        object Valid : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun validateName(name: String): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.Invalid("Station name cannot be blank")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return Result.Invalid("Station name is too long (maximum $MAX_NAME_LENGTH characters)")
        }
        return Result.Valid
    }

    fun validateUrl(urlString: String): Result {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty()) {
            return Result.Invalid("URL cannot be blank")
        }
        if (trimmed.length > MAX_URL_LENGTH) {
            return Result.Invalid("URL is too long (maximum $MAX_URL_LENGTH characters)")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Result.Invalid("Invalid URL syntax")
        }

        val scheme = uri.scheme
        if (scheme == null || !scheme.equals("https", ignoreCase = true)) {
            return Result.Invalid("Only HTTPS stream URLs are supported")
        }

        if (uri.rawUserInfo != null) {
            return Result.Invalid("URL must not contain credentials or user-info")
        }

        if (uri.host.isNullOrBlank()) {
            return Result.Invalid("URL must include a valid host")
        }

        val path = uri.path ?: ""
        if (path.endsWith(".pls", ignoreCase = true) ||
            (path.endsWith(".m3u", ignoreCase = true) && !path.endsWith(".m3u8", ignoreCase = true))) {
            return Result.Invalid("Playlist files (.m3u, .pls) are not supported. Provide a direct stream or HLS (.m3u8) URL.")
        }

        return Result.Valid
    }

    /**
     * Validates both station name and URL.
     */
    fun validate(name: String, url: String): Result {
        val nameRes = validateName(name)
        if (nameRes !is Result.Valid) return nameRes
        return validateUrl(url)
    }
}

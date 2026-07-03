package com.github.kotlintubeexplode.internal

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

/**
 * Hosts the library actually fetches from — watch pages, youtubei, base.js and captions
 * (youtube.com), streams + DASH manifests (googlevideo.com), and youtubei (googleapis.com).
 * Deliberately narrow, and aligned with the certificate-pin set: a response-supplied URL, or a
 * redirect target, pointing anywhere else is treated as SSRF and refused — including other Google
 * properties the library never fetches, which would otherwise be MITM-able redirect launch points.
 */
private val GOOGLE_HOST_SUFFIXES = listOf(
    "youtube.com",
    "googlevideo.com",
    "googleapis.com",
)

/** True if [host] is exactly, or a subdomain of, a Google/YouTube host the library trusts. */
internal fun isGoogleHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    return GOOGLE_HOST_SUFFIXES.any { h == it || h.endsWith(".$it") }
}

/** True if [url] parses, uses https, and targets a trusted Google/YouTube host. */
internal fun isGoogleHttpsUrl(url: String): Boolean {
    val httpUrl = url.toHttpUrlOrNull() ?: return false
    return httpUrl.isHttps && isGoogleHost(httpUrl.host)
}

/**
 * Validates a URL that came from a parsed YouTube response before the library fetches it.
 * Requires https and a Google/YouTube host; anything else (internal IPs, localhost, file://,
 * an attacker's server) throws [IOException]. Returns [url] unchanged when it is safe.
 */
internal fun requireGoogleHttpsUrl(url: String): String {
    if (!isGoogleHttpsUrl(url)) {
        val host = url.toHttpUrlOrNull()?.host ?: url
        throw IOException(
            "Refusing to fetch response-supplied URL outside the YouTube/Google trust boundary: $host"
        )
    }
    return url
}

/**
 * Per-hop trust-boundary enforcement for the library's own HTTP client. Runs as a NETWORK
 * interceptor, so it sees every redirect hop — the call-site allowlist only validates hop 0, and
 * OkHttp then follows a 3xx to any host:
 *  - Throws [IOException] for any host outside the Google trust boundary, BLOCKING SSRF via a
 *    redirect off an allowlisted host. Legit youtube<->googlevideo redirects stay inside the
 *    boundary, so real traffic is unaffected.
 *  - Strips Cookie + Authorization on a cleartext (http) hop, so a https->http downgrade redirect
 *    can't carry the user's SAPISID session in the clear.
 * Returns the (possibly credential-stripped) request to proceed with.
 */
internal fun enforceTrustBoundaryOrThrow(request: Request): Request {
    val url = request.url
    if (!isGoogleHost(url.host)) {
        throw IOException(
            "Refusing to follow a request/redirect outside the YouTube/Google trust boundary: ${url.host}"
        )
    }
    if (!url.isHttps && (request.header("Cookie") != null || request.header("Authorization") != null)) {
        return request.newBuilder()
            .removeHeader("Cookie")
            .removeHeader("Authorization")
            .build()
    }
    return request
}

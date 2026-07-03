package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.exceptions.RequestLimitExceededException
import okhttp3.CertificatePinner
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Internal HTTP controller that wraps OkHttp with YouTube-specific defaults.
 *
 * Provides browser-like headers to avoid detection and handles
 * common HTTP operations used throughout the library.
 *
 * Features:
 * - Cookie management with YouTube consent cookie
 * - SAPISIDHASH authentication for logged-in users
 * - Automatic API key injection
 * - Rate limit (429) detection
 * - Retry logic with exponential backoff
 */
internal class HttpController(
    private val client: OkHttpClient = defaultClient,
    initialCookies: List<Cookie> = emptyList()
) {
    companion object {
        /**
         * Default User-Agent mimicking a modern Chrome browser on Windows.
         * This is critical for avoiding YouTube's bot detection.
         */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Accept-Language header for English content.
         */
        const val DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9"

        /**
         * JSON media type for POST requests.
         */
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * YouTube internal API key.
         * This key doesn't appear to change frequently.
         */
        const val YOUTUBE_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

        /**
         * YouTube consent cookie value.
         * This is required to access some personalized content, such as mix playlists.
         * The cookie is valid for 13 months from the date it was generated.
         */
        const val CONSENT_COOKIE_VALUE = "CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg"

        /**
         * Maximum number of retries for transient 5xx server errors.
         *
         * Mirrors upstream YoutubeHttpHandler.SendAsync, which retries any response with a
         * status >= 500 up to 5 times (6 attempts total) before returning it.
         */
        const val MAX_SERVER_ERROR_RETRIES = 5

        /**
         * Maximum size (bytes) for an in-memory response body read by get()/postJson().
         * Real YouTube responses (watch HTML, youtubei JSON, DASH/caption XML, base.js) are a few
         * MB at most; a body larger than this is refused before it is fully buffered, so a malicious
         * or MITM response advertising a huge/endless body can't OOM the process. Streams are read
         * incrementally via getStream() and are unaffected.
         */
        internal const val MAX_RESPONSE_BYTES = 64L * 1024 * 1024

        /**
         * Certificate pinner for the YouTube / Google hosts the library talks to.
         *
         * Pins the SPKI SHA-256 hashes of Google's current + backup trust anchors (Google Trust
         * Services roots R1–R4 plus the cross-signing GlobalSign Root CA). OkHttp matches a pin
         * against the validated chain's trust anchor, so pinning the roots survives leaf/intermediate
         * rotation; the multiple roots survive a root rotation. Verified against the live youtube.com
         * and googlevideo.com chains.
         *
         * Note: if pinning conflicts with a corporate TLS-inspection proxy, supply your own
         * OkHttpClient (see YoutubeClient) — that path is not pinned.
         *
         * Rotation policy: when Google rotates a root, add the new SPKI pin here BEFORE they cut over
         * (keep the old one until retired) and let HttpsPinningIntegrationTest catch a stale set.
         */
        val certificatePinner: CertificatePinner = CertificatePinner.Builder().apply {
            val googleRoots = arrayOf(
                "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1
                "sha256/Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78=", // GTS Root R2
                "sha256/QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=", // GTS Root R3
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4
                "sha256/K87oWBWM9UZfyddvDfoxL+8lpNyoUB2ptGtn0fv6G2Q=", // GlobalSign Root CA
            )
            for (host in listOf("*.youtube.com", "youtube.com", "*.googlevideo.com", "*.googleapis.com")) {
                add(host, *googleRoots)
            }
        }.build()

        /**
         * Strips Cookie + Authorization when a request (including a redirect hop) targets a host
         * outside the Google trust boundary, so a cross-host 302 can't carry the user's SAPISID
         * session off-site. A network interceptor so it sees every redirect hop.
         */
        val credentialStrippingInterceptor: Interceptor = Interceptor { chain ->
            chain.proceed(enforceTrustBoundaryOrThrow(chain.request()))
        }

        /**
         * Builds an OkHttpClient with the library's security defaults: certificate pinning to the
         * Google roots AND the cross-host credential-stripping network interceptor. Both
         * [defaultClient] and YoutubeClient's owned client use this, so the shipped default path is
         * actually pinned (it previously built a bare, unpinned client and bypassed the pins).
         */
        fun newSecureClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .certificatePinner(certificatePinner)
            .addNetworkInterceptor(credentialStrippingInterceptor)

        /**
         * Singleton OkHttpClient with the library's security defaults (see [newSecureClientBuilder]).
         * A singleton keeps connection pooling effective.
         */
        val defaultClient: OkHttpClient by lazy { newSecureClientBuilder().build() }
    }

    /**
     * In-memory cookie storage.
     */
    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        // Add consent cookie for youtube.com
        val youtubeUrl = "https://www.youtube.com/".toHttpUrlOrNull()
        if (youtubeUrl != null) {
            val consentCookie = Cookie.Builder()
                .name("SOCS")
                .value(CONSENT_COOKIE_VALUE)
                .domain("youtube.com")
                .path("/")
                .secure()
                .build()
            addCookie("youtube.com", consentCookie)
        }

        // Add any user-provided cookies
        for (cookie in initialCookies) {
            addCookie(cookie.domain, cookie)
        }
    }

    private fun addCookie(domain: String, cookie: Cookie) {
        val normalizedDomain = domain.removePrefix(".")
        cookies.getOrPut(normalizedDomain) { mutableListOf() }.add(cookie)
    }

    private fun getCookiesForHost(host: String): List<Cookie> {
        val result = mutableListOf<Cookie>()

        // Direct match
        cookies[host]?.let { result.addAll(it) }

        // Parent domain match (e.g., www.youtube.com matches youtube.com cookies)
        val parts = host.split(".")
        if (parts.size > 2) {
            val parentDomain = parts.drop(1).joinToString(".")
            cookies[parentDomain]?.let { result.addAll(it) }
        }

        // Filter out expired cookies
        // Note: OkHttp sets expiresAt to Long.MAX_VALUE for session cookies (no explicit expiry)
        return result.filter { cookie ->
            cookie.expiresAt > System.currentTimeMillis()
        }
    }

    /**
     * Generates SAPISIDHASH authorization header for authenticated requests.
     *
     * This is used for authenticated API requests when the user has logged in.
     * The header is generated using SHA-1 hash of timestamp, session ID, and origin.
     */
    private fun tryGenerateAuthHeader(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val cookies = getCookiesForHost(httpUrl.host)

        // Find SAPISID or __Secure-3PAPISID cookie
        val sessionId = cookies.firstOrNull { it.name == "__Secure-3PAPISID" }?.value
            ?: cookies.firstOrNull { it.name == "SAPISID" }?.value
            ?: return null

        val timestamp = System.currentTimeMillis() / 1000
        val origin = "${httpUrl.scheme}://${httpUrl.host}"
        val token = "$timestamp $sessionId $origin"

        // SHA-1 hash, uppercase hex to match upstream's Convert.ToHexString.
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(token.toByteArray())
            .joinToString("") { "%02X".format(it) }

        return "SAPISIDHASH ${timestamp}_$hash"
    }

    /**
     * Builds cookie header string from cookies.
     */
    private fun buildCookieHeader(host: String): String? {
        val cookies = getCookiesForHost(host)
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * Processes Set-Cookie headers from response.
     */
    private fun processSetCookieHeaders(url: String, headers: List<String>) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        for (header in headers) {
            try {
                Cookie.parse(httpUrl, header)?.let { cookie ->
                    addCookie(cookie.domain, cookie)
                }
            } catch (e: Exception) {
                // YouTube may send cookies for other domains, ignore them
            }
        }
    }

    /**
     * Prepares the URL with required query parameters.
     */
    private fun prepareUrl(url: String): String {
        var result = url

        // Add API key for internal API requests
        if (url.contains("/youtubei/") && !url.contains("key=")) {
            result = if (url.contains("?")) "$result&key=$YOUTUBE_API_KEY"
            else "$result?key=$YOUTUBE_API_KEY"
        }

        // Add language parameter if not present
        if (!url.contains("hl=")) {
            result = if (result.contains("?")) "$result&hl=en"
            else "$result?hl=en"
        }

        return result
    }

    /**
     * Performs a GET request to the specified URL.
     *
     * @param url The URL to fetch
     * @param headers Additional headers to include (optional)
     * @return The response body as a string
     * @throws IOException if the request fails
     * @throws HttpException if the response status is not successful
     * @throws RequestLimitExceededException if rate limited (HTTP 429)
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val (preparedUrl, requestBuilder) = newStandardRequest(url, headers)
        val request = requestBuilder.get().build()
        executeRequest(request, preparedUrl)
    }

    /**
     * Performs a POST request with JSON body.
     *
     * @param url The URL to post to
     * @param json The JSON body content
     * @param headers Additional headers to include (optional)
     * @return The response body as a string
     * @throws IOException if the request fails
     * @throws HttpException if the response status is not successful
     * @throws RequestLimitExceededException if rate limited (HTTP 429)
     */
    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val (preparedUrl, requestBuilder) = newStandardRequest(url, headers)
        requestBuilder.header("Content-Type", "application/json")
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder.post(body).build()
        executeRequest(request, preparedUrl)
    }

    /**
     * Builds a [Request.Builder] preconfigured with the standard YouTube header pipeline:
     * User-Agent, Accept-Language, Origin, cookies for the host, and SAPISIDHASH
     * Authorization (when we have the SAPISID cookie).
     *
     * Used by all GET-family methods (`get`, `getStream`, `getContentLength`) and `postJson`
     * so authenticated requests are consistent across the entire pipeline. Without this
     * shared path, observers of network traffic could distinguish authenticated vs.
     * unauthenticated callers based on which method was called.
     */
    private fun newStandardRequest(
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<String, Request.Builder> {
        val preparedUrl = prepareUrl(url)
        val httpUrl = preparedUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $url")

        val builder = Request.Builder()
            .url(preparedUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
            .header("Origin", "${httpUrl.scheme}://${httpUrl.host}")

        // Only attach credentials over TLS. A malicious/compromised YouTube response can hand back
        // an http:// youtube.com URL (e.g. a caption baseUrl); cert pinning does not cover a fresh
        // cleartext request, so sending the SAPISID cookie + SAPISIDHASH there would leak the user's
        // Google session to any passive on-path observer.
        if (httpUrl.isHttps) {
            buildCookieHeader(httpUrl.host)?.let { builder.header("Cookie", it) }
            tryGenerateAuthHeader(preparedUrl)?.let { builder.header("Authorization", it) }
        }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        return preparedUrl to builder
    }

    /**
     * Executes an OkHttp call, retrying transient 5xx server errors.
     *
     * Mirrors upstream YoutubeHttpHandler.SendAsync: any response with a status >= 500 is
     * retried up to [MAX_SERVER_ERROR_RETRIES] times (6 attempts total), then the last
     * response is returned for the caller to handle. Every request method routes through
     * here, so getStream/getContentLength get the same resilience as get/postJson.
     */
    private fun executeWithServerErrorRetry(request: Request): okhttp3.Response {
        var retriesRemaining = MAX_SERVER_ERROR_RETRIES
        while (true) {
            val response = client.newCall(request).execute()
            if (response.code >= 500 && retriesRemaining > 0) {
                retriesRemaining--
                response.close()
                continue
            }
            return response
        }
    }

    /**
     * Executes a request and returns the response body.
     */
    private fun executeRequest(request: Request, url: String): String {
        val response = executeWithServerErrorRetry(request)

        return response.use { resp ->
            // Process Set-Cookie headers
            resp.headers("Set-Cookie").let { cookies ->
                if (cookies.isNotEmpty()) {
                    processSetCookieHeaders(url, cookies)
                }
            }

            // Check for rate limiting
            if (resp.code == 429) {
                throw RequestLimitExceededException(
                    "Exceeded request rate limit. Please try again in a few hours. " +
                    "Alternatively, provide cookies for a pre-authenticated user."
                )
            }

            if (!resp.isSuccessful) {
                throw HttpException(resp.code, resp.message, request.url.toString())
            }

            readCappedBody(resp, request.url.toString())
        }
    }

    /**
     * Reads a response body fully into a String, but refuses one larger than [MAX_RESPONSE_BYTES]
     * so a malicious/MITM response can't exhaust memory. Rejects an over-cap advertised
     * Content-Length up front, and bounds the buffered bytes for chunked/unknown-length bodies.
     */
    private fun readCappedBody(response: okhttp3.Response, url: String): String {
        val body = response.body ?: throw IOException("Empty response body from $url")
        val advertised = body.contentLength()
        if (advertised > MAX_RESPONSE_BYTES) {
            throw IOException("Response body from $url is too large ($advertised bytes)")
        }
        val source = body.source()
        // Buffer at most cap+1 bytes so an unknown-length (chunked) body can't blow up memory.
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) {
            throw IOException("Response body from $url exceeds the ${MAX_RESPONSE_BYTES}-byte cap")
        }
        return source.readString(Charsets.UTF_8)
    }

    /**
     * Performs a GET request with retry logic.
     *
     * @param url The URL to fetch
     * @param maxRetries Maximum number of retry attempts
     * @param headers Additional headers to include (optional)
     * @return The response body as a string
     */
    suspend fun getWithRetry(
        url: String,
        maxRetries: Int = 3,
        headers: Map<String, String> = emptyMap()
    ): String {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return get(url, headers)
            } catch (e: RequestLimitExceededException) {
                // Don't retry rate limit exceptions
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    // Exponential backoff: 100ms, 200ms, 400ms...
                    kotlinx.coroutines.delay(100L * (1 shl attempt))
                }
            }
        }

        throw lastException ?: IOException("Failed after $maxRetries retries")
    }

    /**
     * Performs a GET request and returns the response as an InputStream.
     *
     * @param url The URL to fetch
     * @param headers Additional headers to include (optional)
     * @return The response body as an InputStream
     * @throws IOException if the request fails
     * @throws HttpException if the response status is not successful
     */
    suspend fun getStream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): java.io.InputStream = withContext(Dispatchers.IO) {
        val (_, builder) = newStandardRequest(url, headers)
        val request = builder.get().build()

        val response = executeWithServerErrorRetry(request)

        if (response.code == 429) {
            response.close()
            throw RequestLimitExceededException(
                "Exceeded request rate limit. Please try again in a few hours."
            )
        }

        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code, response.message, request.url.toString())
        }

        response.body?.byteStream()
            ?: throw IOException("Empty response body from ${request.url}")
    }

    /**
     * Performs a HEAD request to get content length.
     *
     * @param url The URL to check
     * @return The content length in bytes, or null if not available
     */
    suspend fun getContentLength(url: String): Long? = withContext(Dispatchers.IO) {
        val (_, builder) = newStandardRequest(url)
        val request = builder.head().build()

        val response = executeWithServerErrorRetry(request)
        response.use {
            if (it.isSuccessful) {
                it.header("Content-Length")?.toLongOrNull()
            } else {
                null
            }
        }
    }

    /**
     * Adds cookies to the cookie store.
     *
     * @param cookiesToAdd List of cookies to add
     */
    fun addCookies(cookiesToAdd: List<Cookie>) {
        for (cookie in cookiesToAdd) {
            addCookie(cookie.domain, cookie)
        }
    }

    /**
     * Gets all stored cookies for debugging/inspection.
     */
    fun getAllCookies(): Map<String, List<Cookie>> = cookies.toMap()
}

/**
 * Exception thrown when an HTTP request returns a non-successful status code.
 */
class HttpException(
    val statusCode: Int,
    val statusMessage: String,
    val url: String
) : IOException("HTTP $statusCode $statusMessage for URL: $url")

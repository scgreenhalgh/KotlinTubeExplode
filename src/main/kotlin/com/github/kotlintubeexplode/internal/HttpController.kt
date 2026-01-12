package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.exceptions.RequestLimitExceededException
import okhttp3.CertificatePinner
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
         * Certificate pinner for YouTube and Google Video domains.
         *
         * Pins to Google Trust Services root certificates to prevent MITM attacks.
         * These are the SPKI (Subject Public Key Info) SHA-256 hashes for:
         * - GTS Root R1 (primary)
         * - GTS Root R2 (backup)
         * - GlobalSign Root CA (legacy backup)
         *
         * Note: If certificate pinning causes issues (e.g., corporate proxies),
         * you can provide your own OkHttpClient without pinning.
         */
        val certificatePinner: CertificatePinner = CertificatePinner.Builder()
            // YouTube and related domains
            .add("*.youtube.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=") // GTS Root R1
            .add("*.youtube.com", "sha256/Vfd95BwDeSQo+NUYxVEEb6lqYFPlgS1ygRtH+WhFcSg=") // GTS Root R2
            .add("*.youtube.com", "sha256/iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=") // GlobalSign Root CA
            .add("youtube.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=")
            .add("youtube.com", "sha256/Vfd95BwDeSQo+NUYxVEEb6lqYFPlgS1ygRtH+WhFcSg=")
            .add("youtube.com", "sha256/iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=")
            // Google Video CDN (where streams are served from)
            .add("*.googlevideo.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=")
            .add("*.googlevideo.com", "sha256/Vfd95BwDeSQo+NUYxVEEb6lqYFPlgS1ygRtH+WhFcSg=")
            .add("*.googlevideo.com", "sha256/iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=")
            // Google APIs (for youtubei endpoints)
            .add("*.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=")
            .add("*.googleapis.com", "sha256/Vfd95BwDeSQo+NUYxVEEb6lqYFPlgS1ygRtH+WhFcSg=")
            .add("*.googleapis.com", "sha256/iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=")
            .build()

        /**
         * Singleton OkHttpClient instance with sensible defaults.
         * Using a singleton ensures connection pooling is effective.
         * Includes certificate pinning for YouTube domains to prevent MITM attacks.
         */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .certificatePinner(certificatePinner)
                .build()
        }
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

        // SHA-1 hash
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
        val preparedUrl = prepareUrl(url)
        val httpUrl = preparedUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $url")

        val requestBuilder = Request.Builder()
            .url(preparedUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)

        // Add origin header
        requestBuilder.header("Origin", "${httpUrl.scheme}://${httpUrl.host}")

        // Add cookies
        buildCookieHeader(httpUrl.host)?.let {
            requestBuilder.header("Cookie", it)
        }

        // Add auth header if available
        tryGenerateAuthHeader(preparedUrl)?.let {
            requestBuilder.header("Authorization", it)
        }

        // Add custom headers
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

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
        val preparedUrl = prepareUrl(url)
        val httpUrl = preparedUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $url")

        val body = json.toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url(preparedUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
            .header("Content-Type", "application/json")

        // Add origin header
        requestBuilder.header("Origin", "${httpUrl.scheme}://${httpUrl.host}")

        // Add cookies
        buildCookieHeader(httpUrl.host)?.let {
            requestBuilder.header("Cookie", it)
        }

        // Add auth header if available
        tryGenerateAuthHeader(preparedUrl)?.let {
            requestBuilder.header("Authorization", it)
        }

        // Add custom headers
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        val request = requestBuilder.post(body).build()
        executeRequest(request, preparedUrl)
    }

    /**
     * Executes a request and returns the response body.
     */
    private fun executeRequest(request: Request, url: String): String {
        val response = client.newCall(request).execute()

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

            resp.body?.string()
                ?: throw IOException("Empty response body from ${request.url}")
        }
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
            .apply {
                headers.forEach { (key, value) -> header(key, value) }
            }
            .get()
            .build()

        val response = client.newCall(request).execute()

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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .head()
            .build()

        val response = client.newCall(request).execute()
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

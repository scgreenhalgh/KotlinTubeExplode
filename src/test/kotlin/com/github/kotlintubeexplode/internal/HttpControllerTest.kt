package com.github.kotlintubeexplode.internal

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.MessageDigest

@DisplayName("HttpController")
class HttpControllerTest {

    @Nested
    @DisplayName("standard header pipeline parity")
    inner class StandardHeaderTests {
        // Security finding sec #1: get() and postJson() apply User-Agent, Accept-Language,
        // Origin, cookies, and SAPISIDHASH auth. Previously getStream() and getContentLength()
        // sent only User-Agent. All four GET-family methods should share the same header
        // pipeline so authenticated sessions don't leak inconsistent state and so behavior
        // is uniform regardless of which method is called.

        @Test
        fun `get should send User-Agent Origin and Accept-Language`() = runTest {
            val recorder = RecordingInterceptor()
            val controller = HttpController(client = recorder.buildClient())

            controller.get("https://www.youtube.com/probe")

            val req = recorder.requests.last()
            req.header("User-Agent") shouldNotBe null
            req.header("Accept-Language") shouldNotBe null
            req.header("Origin") shouldContain "youtube.com"
        }

        @Test
        fun `getStream should send User-Agent Origin and Accept-Language`() = runTest {
            val recorder = RecordingInterceptor()
            val controller = HttpController(client = recorder.buildClient())

            controller.getStream("https://www.youtube.com/probe")

            val req = recorder.requests.last()
            req.header("User-Agent") shouldNotBe null
            req.header("Accept-Language") shouldNotBe null
            req.header("Origin") shouldContain "youtube.com"
        }

        @Test
        fun `getContentLength should send User-Agent Origin and Accept-Language`() = runTest {
            val recorder = RecordingInterceptor()
            val controller = HttpController(client = recorder.buildClient())

            controller.getContentLength("https://www.youtube.com/probe")

            val req = recorder.requests.last()
            req.header("User-Agent") shouldNotBe null
            req.header("Accept-Language") shouldNotBe null
            req.header("Origin") shouldContain "youtube.com"
        }
    }

    @Nested
    @DisplayName("5xx server-error retry (drift #19)")
    inner class ServerErrorRetryTests {
        // Upstream YoutubeHttpHandler.SendAsync retries ANY 5xx response up to 5 times
        // (6 attempts total) at the transport level, so every request method benefits.
        // Our retry previously lived only in getWithRetry (3 attempts) and never covered
        // getStream / getContentLength. These tests pin the upstream-matching behavior:
        // 5xx is retried for the stream and content-length paths, up to 5 retries.

        @Test
        fun `getContentLength should retry a transient 5xx then succeed`() = runTest {
            val interceptor = SequenceInterceptor(listOf(503, 200))
            val controller = HttpController(client = interceptor.buildClient())

            val length = controller.getContentLength("https://www.youtube.com/probe")

            length shouldBe 2048L
            interceptor.callCount shouldBe 2
        }

        @Test
        fun `getStream should retry a transient 5xx then succeed`() = runTest {
            val interceptor = SequenceInterceptor(listOf(500, 200))
            val controller = HttpController(client = interceptor.buildClient())

            val stream = shouldNotThrowAny {
                controller.getStream("https://www.youtube.com/probe")
            }
            stream.close()

            interceptor.callCount shouldBe 2
        }

        @Test
        fun `should retry a persistent 5xx exactly five times (six attempts)`() = runTest {
            val interceptor = SequenceInterceptor(listOf(500))
            val controller = HttpController(client = interceptor.buildClient())

            controller.getContentLength("https://www.youtube.com/probe")

            interceptor.callCount shouldBe 6
        }
    }

    @Nested
    @DisplayName("SAPISIDHASH authorization (drift #20)")
    inner class AuthHeaderTests {
        // Upstream builds the SAPISIDHASH token hash with Convert.ToHexString, which emits
        // UPPERCASE hex. We previously formatted with "%02x" (lowercase). Match upstream.

        @Test
        fun `authorization hash should be uppercase hex like upstream`() = runTest {
            val recorder = RecordingInterceptor()
            val sapisid = "test-sapisid-value"
            val cookie = Cookie.Builder()
                .name("SAPISID")
                .value(sapisid)
                .domain("youtube.com")
                .build()
            val controller = HttpController(
                client = recorder.buildClient(),
                initialCookies = listOf(cookie)
            )

            controller.get("https://www.youtube.com/probe")

            val auth = recorder.requests.last().header("Authorization")
            auth shouldNotBe null

            // Header shape: "SAPISIDHASH <timestamp>_<hash>". Recompute using the timestamp
            // echoed back in the header so the expectation is deterministic. Origin mirrors
            // HttpController: "<scheme>://<host>".
            val payload = auth!!.removePrefix("SAPISIDHASH ")
            val (timestamp, actualHash) = payload.split("_", limit = 2)
            val expectedHash = MessageDigest.getInstance("SHA-1")
                .digest("$timestamp $sapisid https://www.youtube.com".toByteArray())
                .joinToString("") { "%02X".format(it) }

            actualHash shouldBe expectedHash
        }
    }

    @Nested
    @DisplayName("cleartext scheme credential guard (security)")
    inner class CleartextGuardTests {
        // A malicious/compromised YouTube response can hand back an http:// youtube.com URL
        // (e.g. a caption baseUrl). Cookies + SAPISIDHASH must NOT ride a cleartext request,
        // or a passive on-path observer harvests the user's Google session.
        private val sapisid = Cookie.Builder()
            .name("SAPISID").value("secret-session").domain("youtube.com").build()

        @Test
        fun `should not attach cookies or auth over cleartext http`() = runTest {
            val recorder = RecordingInterceptor()
            val controller = HttpController(client = recorder.buildClient(), initialCookies = listOf(sapisid))

            controller.get("http://www.youtube.com/api/timedtext?v=x")

            val req = recorder.requests.last()
            req.header("Cookie") shouldBe null
            req.header("Authorization") shouldBe null
        }

        @Test
        fun `should attach cookies and auth over https`() = runTest {
            val recorder = RecordingInterceptor()
            val controller = HttpController(client = recorder.buildClient(), initialCookies = listOf(sapisid))

            controller.get("https://www.youtube.com/api/timedtext?v=x")

            val req = recorder.requests.last()
            req.header("Cookie") shouldNotBe null
            req.header("Authorization") shouldNotBe null
        }
    }

    @Nested
    @DisplayName("response body size cap (security)")
    inner class BodySizeCapTests {
        // A malicious/MITM response advertising a multi-GB body would OOM the client if read
        // whole. get()/postJson() must refuse a body larger than the cap.
        @Test
        fun `should reject a response body advertising more than the cap`() = runTest {
            val controller = HttpController(client = OversizedBodyInterceptor().buildClient())

            shouldThrow<IOException> { controller.get("https://www.youtube.com/probe") }
        }

        @Test
        fun `should accept a normal-sized response body`() = runTest {
            val controller = HttpController(client = RecordingInterceptor().buildClient())

            controller.get("https://www.youtube.com/probe") shouldBe "ok"
        }
    }

    /**
     * Interceptor that serves a scripted sequence of status codes. Successful (2xx)
     * responses carry a Content-Length header so getContentLength can read it back.
     * When the sequence is exhausted, the last code repeats (models a persistent error).
     */
    private class SequenceInterceptor(private val codes: List<Int>) : Interceptor {
        var callCount = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val code = codes[callCount.coerceAtMost(codes.size - 1)]
            callCount++

            val builder = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code < 400) "OK" else "Error")
                .body("body".toResponseBody("text/plain".toMediaType()))
            if (code in 200..299) {
                builder.addHeader("Content-Length", "2048")
            }
            return builder.build()
        }

        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(this)
            .build()
    }

    private class RecordingInterceptor : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests.add(request)

            // Short-circuit a canned 200 response so tests don't hit the network.
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("ok".toResponseBody("text/plain".toMediaType()))
                .addHeader("Content-Length", "1024")
                .build()
        }

        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(this)
            .build()
    }

    /**
     * Serves a 200 whose body ADVERTISES a huge content length (with tiny actual bytes, so the
     * test allocates nothing large). Exercises the advertised-length rejection path of the cap.
     */
    private class OversizedBodyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val body = object : ResponseBody() {
                override fun contentType() = "text/html".toMediaType()
                override fun contentLength() = Long.MAX_VALUE
                override fun source() = Buffer().writeUtf8("tiny")
            }
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }

        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(this)
            .build()
    }
}

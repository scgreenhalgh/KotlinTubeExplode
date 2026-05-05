package com.github.kotlintubeexplode.internal

import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
}

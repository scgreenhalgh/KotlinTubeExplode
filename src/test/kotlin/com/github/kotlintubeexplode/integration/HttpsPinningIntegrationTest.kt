package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.internal.HttpController
import okhttp3.Request
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Verifies the certificate pins actually validate against the live Google chains. If Google rotates
 * a root and the pin set goes stale, this fails LOUDLY here instead of silently breaking every
 * consumer in production. Any HTTP status is fine — we only care that the TLS handshake + pin check
 * pass (no [SSLPeerUnverifiedException]).
 */
@Tag("integration")
@DisplayName("HTTPS certificate pinning")
class HttpsPinningIntegrationTest {

    private val hosts = listOf(
        "https://www.youtube.com/",
        "https://redirector.googlevideo.com/",
        "https://www.googleapis.com/",
    )

    @Test
    fun `pinned client connects to the Google hosts without a pin failure`() {
        val client = HttpController.newSecureClientBuilder().build()

        for (url in hosts) {
            try {
                client.newCall(Request.Builder().url(url).head().build()).execute().use { /* status irrelevant */ }
            } catch (e: SSLPeerUnverifiedException) {
                throw AssertionError("Certificate pinning REJECTED $url — the pin set is stale/wrong: ${e.message}", e)
            }
        }
    }
}

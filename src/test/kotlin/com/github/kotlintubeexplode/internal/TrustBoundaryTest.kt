package com.github.kotlintubeexplode.internal

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import okhttp3.Request
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

@DisplayName("TrustBoundary")
class TrustBoundaryTest {

    @Nested
    @DisplayName("isGoogleHost")
    inner class IsGoogleHostTests {
        @Test
        fun `accepts YouTube and Google hosts`() {
            isGoogleHost("www.youtube.com") shouldBe true
            isGoogleHost("youtube.com") shouldBe true
            isGoogleHost("rr1---sn-abc.googlevideo.com") shouldBe true
            isGoogleHost("youtubei.googleapis.com") shouldBe true
        }

        @Test
        fun `rejects non-Google hosts and suffix tricks`() {
            isGoogleHost("localhost") shouldBe false
            isGoogleHost("169.254.169.254") shouldBe false
            isGoogleHost("evil.com") shouldBe false
            isGoogleHost("notgooglevideo.com") shouldBe false
            isGoogleHost("googlevideo.com.evil.com") shouldBe false
        }

        @Test
        fun `rejects allowlisted-but-never-fetched Google properties (trimmed to what the library fetches)`() {
            // The library only fetches youtube.com / googlevideo.com / googleapis.com. Trusting
            // extra Google suffixes just hands an attacker more MITM-able redirect launch points.
            isGoogleHost("google.com") shouldBe false
            isGoogleHost("www.google.com") shouldBe false
            isGoogleHost("i.ytimg.com") shouldBe false
        }
    }

    @Nested
    @DisplayName("requireGoogleHttpsUrl")
    inner class RequireGoogleHttpsUrlTests {
        @Test
        fun `accepts an https googlevideo url`() {
            shouldNotThrowAny {
                requireGoogleHttpsUrl("https://rr1---sn-abc.googlevideo.com/videoplayback?itag=140")
            }
        }

        @Test
        fun `rejects an internal http url (SSRF)`() {
            shouldThrow<IOException> { requireGoogleHttpsUrl("http://169.254.169.254/latest/meta-data/") }
        }

        @Test
        fun `rejects an https url to a non-Google host`() {
            shouldThrow<IOException> { requireGoogleHttpsUrl("https://evil.com/x") }
        }

        @Test
        fun `rejects a file url`() {
            shouldThrow<IOException> { requireGoogleHttpsUrl("file:///etc/passwd") }
        }

        @Test
        fun `rejects cleartext http to a Google host`() {
            shouldThrow<IOException> { requireGoogleHttpsUrl("http://www.youtube.com/api/timedtext") }
        }
    }

    @Nested
    @DisplayName("enforceTrustBoundaryOrThrow")
    inner class EnforceTrustBoundaryTests {
        // Runs as a NETWORK interceptor, so it sees every redirect hop. The call-site allowlist
        // only validates hop 0; OkHttp then follows a 302 to any host. This must (a) BLOCK any hop
        // outside the trust boundary — not merely strip headers — to stop SSRF via redirect, and
        // (b) strip credentials on a cleartext http hop (a https->http downgrade).
        @Test
        fun `throws for a host outside the trust boundary (blocks SSRF via redirect)`() {
            val req = Request.Builder().url("https://169.254.169.254/latest/meta-data/").build()
            shouldThrow<IOException> { enforceTrustBoundaryOrThrow(req) }
        }

        @Test
        fun `throws for an allowlisted-but-untrusted redirect target`() {
            // e.g. a redirect off an open-redirect landing on a Google property we don't fetch.
            val req = Request.Builder().url("https://www.google.com/url?q=x").build()
            shouldThrow<IOException> { enforceTrustBoundaryOrThrow(req) }
        }

        @Test
        fun `strips Cookie and Authorization on a cleartext http hop`() {
            val req = Request.Builder()
                .url("http://www.youtube.com/api/timedtext")
                .header("Cookie", "SAPISID=secret-session")
                .header("Authorization", "SAPISIDHASH 123_abc")
                .build()

            val out = enforceTrustBoundaryOrThrow(req)

            out.header("Cookie") shouldBe null
            out.header("Authorization") shouldBe null
        }

        @Test
        fun `keeps credentials on an https Google hop`() {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("Cookie", "SAPISID=secret-session")
                .header("Authorization", "SAPISIDHASH 123_abc")
                .build()

            val out = enforceTrustBoundaryOrThrow(req)

            out.header("Cookie") shouldBe "SAPISID=secret-session"
            out.header("Authorization") shouldBe "SAPISIDHASH 123_abc"
        }
    }
}

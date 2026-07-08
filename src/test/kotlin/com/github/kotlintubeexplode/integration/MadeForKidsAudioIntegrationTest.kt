package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.client.YoutubeClient
import com.github.kotlintubeexplode.testdata.VideoIds
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.time.Duration.Companion.seconds

/**
 * Live checks that a made-for-kids video yields downloadable audio.
 *
 * Made-for-kids content is UNPLAYABLE on ANDROID_VR and VISIONOS, and the iOS client exposes only
 * video-only streams (its adaptive audio-only itags 403 behind a PO token). The only poToken-free
 * audio path is the plain ANDROID client's legacy muxed itag-18 (360p H.264 + AAC), so
 * getBestAudioStream() returns that muxed stream. Before the ANDROID fallback existed, the chain
 * stopped at the iOS video-only streams and getBestAudioStream() was null.
 */
@Tag("integration")
@DisplayName("Made-for-kids audio fallback (integration)")
class MadeForKidsAudioIntegrationTest {
    private val client = YoutubeClient()

    @Test
    @DisplayName("made-for-kids video exposes a downloadable audio stream")
    fun `made-for-kids video exposes a downloadable audio stream`(@TempDir tempDir: Path) =
        runTest(timeout = 120.seconds) {
            val manifest = client.streams.getManifest(VideoIds.MadeForKids)

            val audio = manifest.getBestAudioStream()
            audio.shouldNotBeNull()

            // Prove it actually downloads, not just that a URL was surfaced.
            val target = tempDir.resolve("kids-audio.${audio.container}")
            client.streams.download(audio, target.toString())

            target.exists() shouldBe true
            target.fileSize() shouldBe audio.size.bytes
        }
}

package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.videos.streams.Container
import com.github.kotlintubeexplode.videos.streams.VideoQuality
import com.github.kotlintubeexplode.videos.streams.AudioOnlyStreamInfo
import com.github.kotlintubeexplode.videos.streams.VideoOnlyStreamInfo
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DashManifestParserTest {

    private val parser = DashManifestParser()

    @Test
    fun `should parse valid dash manifest`() {
        val manifestXml = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-live:2011">
              <Period>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="140" codecs="mp4a.40.2" bandwidth="129553" audioSamplingRate="44100">
                    <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
                    <BaseURL>https://googlevideo.com/videoplayback/id/140/clen/5000</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="137" codecs="avc1.640028" bandwidth="4500000" width="1920" height="1080" frameRate="30">
                    <BaseURL>https://googlevideo.com/videoplayback/id/137/clen/999999</BaseURL>
                  </Representation>
                  <Representation id="136" codecs="avc1.4d401f" bandwidth="2500000" width="1280" height="720" frameRate="30">
                    <BaseURL>https://googlevideo.com/videoplayback/id/136/clen/555555</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val streams = parser.parse(manifestXml)

        streams shouldHaveSize 3

        // Check Audio (itag 140)
        val audio = streams.filterIsInstance<AudioOnlyStreamInfo>().find { it.url.contains("/140/") }
        audio?.container shouldBe Container.Mp4
        audio?.bitrate?.bitsPerSecond shouldBe 129553L
        audio?.size?.bytes shouldBe 5000L

        // Check 1080p Video (itag 137)
        val video1080 = streams.filterIsInstance<VideoOnlyStreamInfo>().find { it.url.contains("/137/") }
        video1080?.videoQuality?.maxHeight shouldBe 1080
        video1080?.videoResolution?.width shouldBe 1920
        video1080?.size?.bytes shouldBe 999999L

        // Check 720p Video (itag 136)
        val video720 = streams.filterIsInstance<VideoOnlyStreamInfo>().find { it.url.contains("/136/") }
        video720?.videoQuality?.maxHeight shouldBe 720
        video720?.videoResolution?.height shouldBe 720
    }

    @Test
    fun `should ignore segmented streams`() {
        val manifestXml = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="137" codecs="avc1.640028" bandwidth="4500000" width="1920" height="1080" frameRate="30">
                    <BaseURL>https://googlevideo.com/videoplayback/id/137</BaseURL>
                    <Initialization sourceURL="sq/0"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val streams = parser.parse(manifestXml)
        streams shouldHaveSize 0
    }
}

package com.github.kotlintubeexplode.videos.closedcaptions

import com.github.kotlintubeexplode.common.Language
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@DisplayName("Closed Captions module")
class ClosedCaptionsTest {

    @Nested
    @DisplayName("ClosedCaptionPart")
    inner class ClosedCaptionPartTests {

        @Test
        fun `should create part with text and offset`() {
            val part = ClosedCaptionPart("Hello", 500.milliseconds)

            part.text shouldBe "Hello"
            part.offset shouldBe 500.milliseconds
        }

        @Test
        fun `toString should return text`() {
            val part = ClosedCaptionPart("World", 1.seconds)

            part.toString() shouldBe "World"
        }
    }

    @Nested
    @DisplayName("ClosedCaption")
    inner class ClosedCaptionTests {

        @Test
        fun `should create caption with all properties`() {
            val parts = listOf(
                ClosedCaptionPart("Hello", 0.milliseconds),
                ClosedCaptionPart("World", 500.milliseconds)
            )
            val caption = ClosedCaption(
                text = "Hello World",
                offset = 1.seconds,
                duration = 2.seconds,
                parts = parts
            )

            caption.text shouldBe "Hello World"
            caption.offset shouldBe 1.seconds
            caption.duration shouldBe 2.seconds
            caption.parts.size shouldBe 2
        }

        @Test
        fun `should create caption with empty parts`() {
            val caption = ClosedCaption(
                text = "Simple caption",
                offset = 5.seconds,
                duration = 3.seconds
            )

            caption.parts shouldBe emptyList()
        }

        @Test
        fun `tryGetPartByTime should return part at time`() {
            val parts = listOf(
                ClosedCaptionPart("First", 0.milliseconds),
                ClosedCaptionPart("Second", 500.milliseconds),
                ClosedCaptionPart("Third", 1.seconds)
            )
            val caption = ClosedCaption("Full text", 0.seconds, 2.seconds, parts)

            val part = caption.tryGetPartByTime(400.milliseconds)
            part shouldNotBe null
            part?.text shouldBe "Second"
        }

        @Test
        fun `tryGetPartByTime should return null when no part at time`() {
            val parts = listOf(ClosedCaptionPart("Only", 1.seconds))
            val caption = ClosedCaption("Only", 0.seconds, 2.seconds, parts)

            caption.tryGetPartByTime(2.seconds) shouldBe null
        }

        @Test
        fun `getPartByTime should throw when no part found`() {
            val caption = ClosedCaption("Text", 0.seconds, 2.seconds)

            shouldThrow<NoSuchElementException> {
                caption.getPartByTime(1.seconds)
            }
        }

        @Test
        fun `toString should return text`() {
            val caption = ClosedCaption("My caption", 0.seconds, 1.seconds)

            caption.toString() shouldBe "My caption"
        }
    }

    @Nested
    @DisplayName("ClosedCaptionTrack")
    inner class ClosedCaptionTrackTests {

        private val sampleCaptions = listOf(
            ClosedCaption("First caption", 0.seconds, 2.seconds),
            ClosedCaption("Second caption", 3.seconds, 2.seconds),
            ClosedCaption("Third caption", 6.seconds, 2.seconds)
        )

        @Test
        fun `should create track with captions`() {
            val track = ClosedCaptionTrack(sampleCaptions)

            track.captions.size shouldBe 3
            track.size shouldBe 3
            track.isEmpty shouldBe false
        }

        @Test
        fun `tryGetByTime should return caption at time`() {
            val track = ClosedCaptionTrack(sampleCaptions)

            val caption = track.tryGetByTime(1.seconds)
            caption shouldNotBe null
            caption?.text shouldBe "First caption"
        }

        @Test
        fun `tryGetByTime should return null between captions`() {
            val track = ClosedCaptionTrack(sampleCaptions)

            track.tryGetByTime(2500.milliseconds) shouldBe null
        }

        @Test
        fun `getByTime should throw when no caption found`() {
            val track = ClosedCaptionTrack(sampleCaptions)

            shouldThrow<NoSuchElementException> {
                track.getByTime(10.seconds)
            }
        }

        @Test
        fun `empty track should have isEmpty true`() {
            val track = ClosedCaptionTrack(emptyList())

            track.isEmpty shouldBe true
            track.size shouldBe 0
        }
    }

    @Nested
    @DisplayName("ClosedCaptionTrackInfo")
    inner class ClosedCaptionTrackInfoTests {

        @Test
        fun `should create track info`() {
            val info = ClosedCaptionTrackInfo(
                url = "https://example.com/captions",
                language = Language("en", "English"),
                isAutoGenerated = false
            )

            info.url shouldBe "https://example.com/captions"
            info.language.code shouldBe "en"
            info.language.name shouldBe "English"
            info.isAutoGenerated shouldBe false
        }

        @Test
        fun `should identify auto-generated tracks`() {
            val autoGenerated = ClosedCaptionTrackInfo(
                url = "https://example.com/auto",
                language = Language("en", "English (auto-generated)"),
                isAutoGenerated = true
            )

            autoGenerated.isAutoGenerated shouldBe true
        }

        @Test
        fun `toString should show language`() {
            val info = ClosedCaptionTrackInfo(
                url = "https://example.com/captions",
                language = Language("ja", "Japanese"),
                isAutoGenerated = false
            )

            info.toString() shouldBe "CC Track (Japanese (ja))"
        }
    }

    @Nested
    @DisplayName("ClosedCaptionManifest")
    inner class ClosedCaptionManifestTests {

        private val sampleTracks = listOf(
            ClosedCaptionTrackInfo("https://example.com/en", Language("en", "English"), false),
            ClosedCaptionTrackInfo("https://example.com/en-auto", Language("en", "English (auto)"), true),
            ClosedCaptionTrackInfo("https://example.com/es", Language("es", "Spanish"), false),
            ClosedCaptionTrackInfo("https://example.com/ja", Language("ja", "Japanese"), true)
        )

        @Test
        fun `should create manifest with tracks`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            manifest.tracks.size shouldBe 4
            manifest.size shouldBe 4
            manifest.isEmpty shouldBe false
        }

        @Test
        fun `tryGetByLanguage should find by code`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            val track = manifest.tryGetByLanguage("es")
            track shouldNotBe null
            track?.language?.code shouldBe "es"
        }

        @Test
        fun `tryGetByLanguage should find by name`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            val track = manifest.tryGetByLanguage("Japanese")
            track shouldNotBe null
            track?.language?.code shouldBe "ja"
        }

        @Test
        fun `tryGetByLanguage should be case insensitive`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            manifest.tryGetByLanguage("EN") shouldNotBe null
            manifest.tryGetByLanguage("spanish") shouldNotBe null
        }

        @Test
        fun `tryGetByLanguage should return null for unknown`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            manifest.tryGetByLanguage("fr") shouldBe null
            manifest.tryGetByLanguage("French") shouldBe null
        }

        @Test
        fun `getByLanguage should throw for unknown language`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            shouldThrow<NoSuchElementException> {
                manifest.getByLanguage("fr")
            }
        }

        @Test
        fun `getAutoGeneratedTracks should filter correctly`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            val autoTracks = manifest.getAutoGeneratedTracks()
            autoTracks.size shouldBe 2
            autoTracks.all { it.isAutoGenerated } shouldBe true
        }

        @Test
        fun `getManualTracks should filter correctly`() {
            val manifest = ClosedCaptionManifest(sampleTracks)

            val manualTracks = manifest.getManualTracks()
            manualTracks.size shouldBe 2
            manualTracks.none { it.isAutoGenerated } shouldBe true
        }

        @Test
        fun `empty manifest should work correctly`() {
            val manifest = ClosedCaptionManifest(emptyList())

            manifest.isEmpty shouldBe true
            manifest.size shouldBe 0
            manifest.tryGetByLanguage("en") shouldBe null
            manifest.getAutoGeneratedTracks() shouldBe emptyList()
        }
    }
}

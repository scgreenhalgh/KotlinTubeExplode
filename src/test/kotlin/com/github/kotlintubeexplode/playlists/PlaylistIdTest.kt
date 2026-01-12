package com.github.kotlintubeexplode.playlists

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PlaylistId")
class PlaylistIdTest {

    @Nested
    @DisplayName("parse")
    inner class ParseTests {

        @ParameterizedTest
        @ValueSource(strings = [
            "PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "OLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "UUxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "FLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "RDxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        ])
        fun `should accept valid raw playlist IDs`(id: String) {
            val playlistId = PlaylistId.parse(id)
            playlistId.value shouldBe id
        }

        @Test
        fun `should extract ID from watch URL with list parameter`() {
            val playlistId = PlaylistId.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
            playlistId.value shouldBe "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        }

        @Test
        fun `should extract ID from playlist URL`() {
            val playlistId = PlaylistId.parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
            playlistId.value shouldBe "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        }

        @Test
        fun `should handle URL with other parameters`() {
            val playlistId = PlaylistId.parse("https://www.youtube.com/playlist?list=PLxxxxxxxx&index=5")
            playlistId.value shouldBe "PLxxxxxxxx"
        }

        @Test
        fun `should throw for invalid ID`() {
            // IDs with special chars or too short are invalid
            shouldThrow<IllegalArgumentException> {
                PlaylistId.parse("a") // too short
            }
            shouldThrow<IllegalArgumentException> {
                PlaylistId.parse("ab@cd") // contains invalid char
            }
        }

        @Test
        fun `should throw for URL without list parameter`() {
            shouldThrow<IllegalArgumentException> {
                PlaylistId.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            }
        }
    }

    @Nested
    @DisplayName("tryParse")
    inner class TryParseTests {

        @Test
        fun `should return null for invalid ID`() {
            // Single char is too short
            PlaylistId.tryParse("a") shouldBe null
            // Special chars are invalid
            PlaylistId.tryParse("ab@cd") shouldBe null
            // Empty string is invalid
            PlaylistId.tryParse("") shouldBe null
        }

        @Test
        fun `should return PlaylistId for valid ID`() {
            val playlistId = PlaylistId.tryParse("PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
            playlistId?.value shouldBe "PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        }
    }

    @Nested
    @DisplayName("isValid")
    inner class IsValidTests {

        @Test
        fun `should return true for valid ID`() {
            PlaylistId.isValid("PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") shouldBe true
        }

        @Test
        fun `should return false for invalid ID`() {
            // Single char is too short
            PlaylistId.isValid("a") shouldBe false
            // Special chars are invalid
            PlaylistId.isValid("ab@cd") shouldBe false
            // Empty string is invalid
            PlaylistId.isValid("") shouldBe false
        }

        @Test
        fun `should return true for valid URL`() {
            PlaylistId.isValid("https://www.youtube.com/playlist?list=PLxxxxxxxx") shouldBe true
        }
    }

    @Nested
    @DisplayName("url property")
    inner class UrlTests {

        @Test
        fun `should generate correct playlist URL`() {
            val playlistId = PlaylistId("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
            playlistId.url shouldBe "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        }
    }

    @Nested
    @DisplayName("toString")
    inner class ToStringTests {

        @Test
        fun `should return the raw ID value`() {
            val playlistId = PlaylistId("PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
            playlistId.toString() shouldBe "PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        }
    }
}

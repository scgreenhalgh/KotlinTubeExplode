package com.github.kotlintubeexplode.channels

import com.github.kotlintubeexplode.testdata.ChannelSlugs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Channel ID types")
class ChannelIdTest {

    @Nested
    @DisplayName("ChannelId")
    inner class ChannelIdTests {

        @Test
        fun `should accept valid channel ID starting with UC`() {
            val channelId = ChannelId.parse("UCuAXFkgsw1L7xaCfnd5JJOw")
            channelId.value shouldBe "UCuAXFkgsw1L7xaCfnd5JJOw"
        }

        @Test
        fun `should extract ID from channel URL`() {
            val channelId = ChannelId.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw")
            channelId.value shouldBe "UCuAXFkgsw1L7xaCfnd5JJOw"
        }

        @Test
        fun `should throw for invalid ID`() {
            shouldThrow<IllegalArgumentException> {
                ChannelId.parse("invalid")
            }
        }

        @Test
        fun `should generate correct channel URL`() {
            val channelId = ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw")
            channelId.url shouldBe "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
        }

        @Test
        fun `tryParse should return null for invalid ID`() {
            ChannelId.tryParse("invalid") shouldBe null
        }

        @Test
        fun `tryParse should return null for truncated UC-only channel ID`() {
            // YouTube sometimes returns malformed channel IDs like just "UC"
            // These should be rejected as invalid
            ChannelId.tryParse("UC") shouldBe null
        }

        @Test
        fun `tryParse should return null for short UC prefix IDs`() {
            ChannelId.tryParse("UC123") shouldBe null
        }

        @Test
        fun `isValid should return true for valid ID`() {
            ChannelId.isValid("UCuAXFkgsw1L7xaCfnd5JJOw") shouldBe true
        }

        @Test
        fun `isValid should return false for invalid ID`() {
            ChannelId.isValid("invalid") shouldBe false
        }
    }

    @Nested
    @DisplayName("UserName")
    inner class UserNameTests {

        @Test
        fun `should accept valid username`() {
            val userName = UserName.parse("GoogleDevelopers")
            userName.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should extract from user URL`() {
            val userName = UserName.parse("https://www.youtube.com/user/GoogleDevelopers")
            userName.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should throw for invalid username`() {
            shouldThrow<IllegalArgumentException> {
                UserName.parse("@invalid")
            }
        }

        @Test
        fun `should generate correct URL`() {
            val userName = UserName("GoogleDevelopers")
            userName.url shouldBe "https://www.youtube.com/user/GoogleDevelopers"
        }
    }

    @Nested
    @DisplayName("ChannelSlug")
    inner class ChannelSlugTests {

        @Test
        fun `should accept valid alphanumeric slug`() {
            val slug = ChannelSlug.parse("GoogleDevelopers")
            slug.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should accept unicode-letter slug`() {
            // Mirrors upstream TestData/ChannelSlugs.cs "Normal". Validation uses
            // Char.isLetterOrDigit (Unicode-aware), so non-ASCII letters are valid.
            val slug = ChannelSlug.parse(ChannelSlugs.Normal)
            slug.value shouldBe ChannelSlugs.Normal
        }

        @Test
        fun `should extract alphanumeric slug from custom URL`() {
            val slug = ChannelSlug.parse("https://www.youtube.com/c/LinusTechTips")
            slug.value shouldBe "LinusTechTips"
        }

        @Test
        fun `should generate correct URL`() {
            val slug = ChannelSlug("GoogleDevelopers")
            slug.url shouldBe "https://www.youtube.com/c/GoogleDevelopers"
        }

        // Drift #7: upstream ChannelSlug.IsValid restricts to char.IsLetterOrDigit only
        // (Feb 2024). We previously also accepted '-', '_' and '.'; those are now rejected.
        @Test
        fun `should reject slug containing a hyphen`() {
            ChannelSlug.tryParse("google-developers") shouldBe null
        }

        @Test
        fun `should reject slug containing an underscore`() {
            ChannelSlug.tryParse("google_developers") shouldBe null
        }

        @Test
        fun `should reject slug containing a dot`() {
            ChannelSlug.tryParse("google.developers") shouldBe null
        }

        @Test
        fun `should reject a non-alphanumeric slug extracted from a custom URL`() {
            ChannelSlug.isValid("https://www.youtube.com/c/google-developers") shouldBe false
        }
    }

    @Nested
    @DisplayName("ChannelHandle")
    inner class ChannelHandleTests {

        @Test
        fun `should accept valid handle`() {
            val handle = ChannelHandle.parse("GoogleDevelopers")
            handle.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should extract from handle URL with @`() {
            val handle = ChannelHandle.parse("https://www.youtube.com/@GoogleDevelopers")
            handle.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should strip leading @ from raw handle`() {
            val handle = ChannelHandle.parse("@GoogleDevelopers")
            handle.value shouldBe "GoogleDevelopers"
        }

        @Test
        fun `should generate correct URL`() {
            val handle = ChannelHandle("GoogleDevelopers")
            handle.url shouldBe "https://www.youtube.com/@GoogleDevelopers"
        }

        @Test
        fun `should throw for invalid handle`() {
            shouldThrow<IllegalArgumentException> {
                ChannelHandle.parse("")
            }
        }
    }
}

package com.github.kotlintubeexplode.channels

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
        fun `should accept valid slug`() {
            val slug = ChannelSlug.parse("google-developers")
            slug.value shouldBe "google-developers"
        }

        @Test
        fun `should extract from custom URL`() {
            val slug = ChannelSlug.parse("https://www.youtube.com/c/google-developers")
            slug.value shouldBe "google-developers"
        }

        @Test
        fun `should generate correct URL`() {
            val slug = ChannelSlug("google-developers")
            slug.url shouldBe "https://www.youtube.com/c/google-developers"
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

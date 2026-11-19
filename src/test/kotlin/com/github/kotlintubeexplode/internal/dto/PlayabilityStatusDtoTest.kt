package com.github.kotlintubeexplode.internal.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlayabilityStatusDto")
class PlayabilityStatusDtoTest {

    @Nested
    @DisplayName("isAgeRestricted")
    inner class IsAgeRestrictedTests {
        // Status-based positives.

        @Test
        fun `LOGIN_REQUIRED is treated as age-restricted`() {
            PlayabilityStatusDto(status = "LOGIN_REQUIRED").isAgeRestricted shouldBe true
        }

        @Test
        fun `CONTENT_CHECK_REQUIRED is treated as age-restricted`() {
            PlayabilityStatusDto(status = "CONTENT_CHECK_REQUIRED").isAgeRestricted shouldBe true
        }

        // Specific phrase positive.

        @Test
        fun `Sign in to confirm your age phrase is age-restricted`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "Sign in to confirm your age"
            ).isAgeRestricted shouldBe true
        }

        // False-positive guards: reasons containing "age" as a substring should NOT trigger.

        @Test
        fun `language unsupported reason is not age-restricted`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "This video is not available in your language"
            ).isAgeRestricted shouldBe false
        }

        @Test
        fun `storage region reason is not age-restricted`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "Insufficient storage to play this video"
            ).isAgeRestricted shouldBe false
        }

        @Test
        fun `package error reason is not age-restricted`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "Cancelled package subscription"
            ).isAgeRestricted shouldBe false
        }

        @Test
        fun `engagement reason is not age-restricted`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "Low engagement video"
            ).isAgeRestricted shouldBe false
        }

        @Test
        fun `null status and reason is not age-restricted`() {
            PlayabilityStatusDto().isAgeRestricted shouldBe false
        }
    }

    @Nested
    @DisplayName("isBotCheck")
    inner class IsBotCheckTests {
        // YouTube's bot check, as captured live from ANDROID_VR on 2026-11-18. The reason uses a
        // curly apostrophe; match on "not a bot" so the apostrophe doesn't matter.

        @Test
        fun `LOGIN_REQUIRED with the not-a-bot reason is a bot check`() {
            PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm you’re not a bot"
            ).isBotCheck shouldBe true
        }

        @Test
        fun `a straight apostrophe in the reason is still a bot check`() {
            PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm you're not a bot"
            ).isBotCheck shouldBe true
        }

        @Test
        fun `status and reason are matched case-insensitively`() {
            PlayabilityStatusDto(
                status = "login_required",
                reason = "SIGN IN TO CONFIRM YOU’RE NOT A BOT"
            ).isBotCheck shouldBe true
        }

        @Test
        fun `an age gate is not a bot check`() {
            PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm your age"
            ).isBotCheck shouldBe false
        }

        @Test
        fun `a private video is not a bot check`() {
            PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "This video is private"
            ).isBotCheck shouldBe false
        }

        @Test
        fun `the not-a-bot reason with another status is not a bot check`() {
            PlayabilityStatusDto(
                status = "ERROR",
                reason = "Sign in to confirm you’re not a bot"
            ).isBotCheck shouldBe false
        }

        @Test
        fun `LOGIN_REQUIRED without a reason is not a bot check`() {
            PlayabilityStatusDto(status = "LOGIN_REQUIRED").isBotCheck shouldBe false
        }
    }
}

package com.github.kotlintubeexplode.internal.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlaylistPanelVideoRendererDto")
class PlaylistPanelVideoRendererDtoTest {

    @Nested
    @DisplayName("durationSeconds")
    inner class DurationSecondsTests {

        @Test
        fun `should parse integer lengthSeconds`() {
            val dto = PlaylistPanelVideoRendererDto(lengthSeconds = "212")
            dto.durationSeconds shouldBe 212L
        }

        @Test
        fun `should parse fractional lengthSeconds and truncate`() {
            // Upstream uses double.ParseOrNull and then TimeSpan.FromSeconds — accepts decimals.
            // We truncate to Long but should not reject the value entirely.
            val dto = PlaylistPanelVideoRendererDto(lengthSeconds = "8.5")
            dto.durationSeconds shouldBe 8L
        }

        @Test
        fun `should fall back to lengthText simpleText`() {
            val dto = PlaylistPanelVideoRendererDto(
                lengthText = TextRunsDto(simpleText = "3:45")
            )
            dto.durationSeconds shouldBe 225L
        }

        @Test
        fun `should fall back to lengthText runs concatenated`() {
            val dto = PlaylistPanelVideoRendererDto(
                lengthText = TextRunsDto(
                    runs = listOf(TextRunDto(text = "1:23"), TextRunDto(text = ":45"))
                )
            )
            dto.durationSeconds shouldBe (1L * 3600 + 23 * 60 + 45)
        }

        @Test
        fun `should parse h_mm_ss`() {
            val dto = PlaylistPanelVideoRendererDto(
                lengthText = TextRunsDto(simpleText = "1:23:45")
            )
            dto.durationSeconds shouldBe (1L * 3600 + 23 * 60 + 45)
        }

        @Test
        fun `should return null when both lengthSeconds and lengthText absent`() {
            val dto = PlaylistPanelVideoRendererDto()
            dto.durationSeconds shouldBe null
        }
    }
}

package com.github.kotlintubeexplode.internal.dto

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

    @Nested
    @DisplayName("authorChannelId")
    inner class AuthorChannelIdTests {

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        @Test
        fun `should fall back to multi-author dialog onTap browseId when byline has no browseEndpoint`() {
            // Multi-author videos (music tracks with featured artists) don't expose the uploader
            // channel via longBylineText.runs[0].navigationEndpoint.browseEndpoint. The channel link
            // lives behind a "..." dialog. Upstream PlaylistVideoData.ChannelId recovers it via
            // navigationEndpoint.showDialogCommand -> ... -> listItems[0] -> onTap -> browseId.
            val raw = """
                {
                  "videoId": "abc123",
                  "title": { "simpleText": "Multi-author track" },
                  "longBylineText": {
                    "runs": [
                      {
                        "text": "Featured Artist",
                        "navigationEndpoint": {
                          "showDialogCommand": {
                            "panelLoadingStrategy": {
                              "inlineContent": {
                                "dialogViewModel": {
                                  "customContent": {
                                    "listViewModel": {
                                      "listItems": [
                                        {
                                          "listItemViewModel": {
                                            "rendererContext": {
                                              "commandContext": {
                                                "onTap": {
                                                  "innertubeCommand": {
                                                    "browseEndpoint": { "browseId": "UCdeepfallback123" }
                                                  }
                                                }
                                              }
                                            }
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
                }
            """.trimIndent()

            val dto = json.decodeFromString<PlaylistPanelVideoRendererDto>(raw)

            // Sanity: the author NAME is recoverable today (only the channel id needs the fallback).
            dto.authorName shouldBe "Featured Artist"
            // Before the fix authorChannelId is null (no dialog fallback) -> this assertion FAILS.
            dto.authorChannelId shouldBe "UCdeepfallback123"
        }

        @Test
        fun `returns null when the dialog-fallback browseId is explicit JSON null`() {
            // Same dialog shape as above, but the terminal browseId is an explicit JSON null.
            // JsonNull is itself a JsonPrimitive whose .content is the string "null", so the naive
            // (current as? JsonPrimitive)?.content leaks the literal "null" as a channel id.
            val raw = """
                {
                  "videoId": "abc123",
                  "title": { "simpleText": "Multi-author track" },
                  "longBylineText": {
                    "runs": [
                      {
                        "text": "Featured Artist",
                        "navigationEndpoint": {
                          "showDialogCommand": {
                            "panelLoadingStrategy": {
                              "inlineContent": {
                                "dialogViewModel": {
                                  "customContent": {
                                    "listViewModel": {
                                      "listItems": [
                                        {
                                          "listItemViewModel": {
                                            "rendererContext": {
                                              "commandContext": {
                                                "onTap": {
                                                  "innertubeCommand": {
                                                    "browseEndpoint": { "browseId": null }
                                                  }
                                                }
                                              }
                                            }
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
                }
            """.trimIndent()

            val dto = json.decodeFromString<PlaylistPanelVideoRendererDto>(raw)

            // Sanity: the walk reaches the terminal node; only the value is JSON null.
            dto.authorName shouldBe "Featured Artist"
            // Pre-fix: JsonNull.content == "null", so authorChannelId is the string "null" -> FAILS.
            dto.authorChannelId shouldBe null
        }
    }
}

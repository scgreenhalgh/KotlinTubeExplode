package com.github.kotlintubeexplode.internal.dto

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlaylistNextResponseDto")
class PlaylistNextResponseDtoTest {

    // Same Json config as PlaylistController — the real deserialization path.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Nested
    @DisplayName("defensive panel title shape handling")
    inner class TitleShapeTests {

        @Test
        fun `object-shaped panel title must not break video extraction`() {
            // YouTube has repeatedly migrated plain-string fields to {simpleText}/{runs} objects.
            // A shape change on the panel title (which pagination never even reads) must not wipe
            // out the whole video batch. Upstream is immune because it reads via GetStringOrNull.
            val raw = """
                {"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{
                  "title":{"simpleText":"My Playlist"},
                  "contents":[{"playlistPanelVideoRenderer":{
                    "videoId":"abc","navigationEndpoint":{"watchEndpoint":{"index":3}}
                  }}]
                }}}}}
            """.trimIndent()

            val response = shouldNotThrowAny {
                json.decodeFromString(PlaylistNextResponseDto.serializer(), raw)
            }

            response.isAvailable shouldBe true
            response.videos.size shouldBe 1
            response.videos.first().videoId shouldBe "abc"
            response.title shouldBe "My Playlist"
        }

        @Test
        fun `runs-shaped panel title is extracted and concatenated`() {
            val raw = """
                {"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{
                  "title":{"runs":[{"text":"Foo "},{"text":"Bar"}]},
                  "contents":[]
                }}}}}
            """.trimIndent()

            val response = json.decodeFromString(PlaylistNextResponseDto.serializer(), raw)

            response.title shouldBe "Foo Bar"
        }

        @Test
        fun `plain string panel title still reads through`() {
            val raw = """
                {"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{
                  "title":"Plain Title","contents":[]
                }}}}}
            """.trimIndent()

            val response = json.decodeFromString(PlaylistNextResponseDto.serializer(), raw)

            response.title shouldBe "Plain Title"
        }
    }
}

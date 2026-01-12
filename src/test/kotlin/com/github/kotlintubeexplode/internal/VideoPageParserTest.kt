package com.github.kotlintubeexplode.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VideoPageParser")
class VideoPageParserTest {

    private lateinit var parser: VideoPageParser

    @BeforeEach
    fun setup() {
        parser = VideoPageParser()
    }

    @Nested
    @DisplayName("parseWatchPage")
    inner class ParseWatchPageTests {

        @Test
        fun `should parse player response from standard HTML`() {
            val html = createMockHtml(
                videoId = "dQw4w9WgXcQ",
                title = "Rick Astley - Never Gonna Give You Up",
                author = "RickAstleyVEVO",
                lengthSeconds = "212",
                viewCount = "1234567890"
            )

            val response = parser.parseWatchPage(html)

            response.videoDetails.shouldNotBeNull()
            response.videoDetails?.videoId shouldBe "dQw4w9WgXcQ"
            response.videoDetails?.title shouldBe "Rick Astley - Never Gonna Give You Up"
            response.videoDetails?.author shouldBe "RickAstleyVEVO"
            response.videoDetails?.durationSeconds shouldBe 212L
            response.videoDetails?.viewCountLong shouldBe 1234567890L
        }

        @Test
        fun `should parse playability status`() {
            val html = createMockHtml(status = "OK")

            val response = parser.parseWatchPage(html)

            response.playabilityStatus.shouldNotBeNull()
            response.playabilityStatus?.isPlayable shouldBe true
        }

        @Test
        fun `should handle unavailable video`() {
            val html = createMockHtml(
                status = "UNPLAYABLE",
                reason = "Video unavailable"
            )

            val response = parser.parseWatchPage(html)

            response.playabilityStatus?.isPlayable shouldBe false
            response.playabilityStatus?.reason shouldBe "Video unavailable"
        }

        @Test
        fun `should throw VideoParseException when no player response found`() {
            val html = "<html><body>No player response here</body></html>"

            shouldThrow<VideoParseException> {
                parser.parseWatchPage(html)
            }
        }

        @Test
        fun `should parse thumbnails`() {
            val html = createMockHtml(
                videoId = "test123test",
                includeThumbnails = true
            )

            val response = parser.parseWatchPage(html)

            response.videoDetails?.thumbnail.shouldNotBeNull()
            response.videoDetails?.thumbnail?.thumbnails.shouldNotBeNull()
            response.videoDetails?.thumbnail?.thumbnails?.isNotEmpty() shouldBe true
        }
    }

    @Nested
    @DisplayName("extractPlayerResponseJson")
    inner class ExtractJsonTests {

        @Test
        fun `should extract JSON from var ytInitialPlayerResponse pattern`() {
            val html = """
                <html>
                <script>
                    var ytInitialPlayerResponse = {"videoDetails": {"videoId": "test123"}};
                </script>
                </html>
            """.trimIndent()

            val json = parser.extractPlayerResponseJson(html)

            json.shouldNotBeNull()
            json shouldBe """{"videoDetails": {"videoId": "test123"}}"""
        }

        @Test
        fun `should extract JSON from window assignment pattern`() {
            val html = """
                <html>
                <script>
                    window["ytInitialPlayerResponse"] = {"videoDetails": {"videoId": "abc"}};
                </script>
                </html>
            """.trimIndent()

            val json = parser.extractPlayerResponseJson(html)

            json.shouldNotBeNull()
            json shouldBe """{"videoDetails": {"videoId": "abc"}}"""
        }

        @Test
        fun `should handle nested JSON objects`() {
            val html = """
                <html>
                <script>
                    var ytInitialPlayerResponse = {"outer": {"inner": {"deep": "value"}}};
                </script>
                </html>
            """.trimIndent()

            val json = parser.extractPlayerResponseJson(html)

            json.shouldNotBeNull()
            json shouldBe """{"outer": {"inner": {"deep": "value"}}}"""
        }

        @Test
        fun `should return null when no pattern matches`() {
            val html = "<html><body>No JavaScript here</body></html>"

            parser.extractPlayerResponseJson(html).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("extractPlayerScriptUrl")
    inner class ExtractPlayerScriptUrlTests {

        @Test
        fun `should extract player script URL with version`() {
            val html = """
                <script src="/s/player/abc12345/player_ias.vflset/en_US/base.js"></script>
            """.trimIndent()

            val url = parser.extractPlayerScriptUrl(html)

            url shouldBe "https://www.youtube.com/s/player/abc12345/player_ias.vflset/en_US/base.js"
        }

        @Test
        fun `should extract from alternate format`() {
            val html = """
                <script src="/player/def67890/base.js"></script>
            """.trimIndent()

            val url = parser.extractPlayerScriptUrl(html)

            url shouldBe "https://www.youtube.com/player/def67890/base.js"
        }

        @Test
        fun `should return null when no script found`() {
            val html = "<html><body>No player script</body></html>"

            parser.extractPlayerScriptUrl(html).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("extractPlayerVersion")
    inner class ExtractPlayerVersionTests {

        @Test
        fun `should extract version from iframe_api content`() {
            val content = """
                (function(){var www='www.youtube.com';if(document.domain==www)document.domain=www;
                var url='https://www.youtube.com/s/player\/a1b2c3d4\/';
            """.trimIndent()

            val version = parser.extractPlayerVersion(content)

            version shouldBe "a1b2c3d4"
        }

        @Test
        fun `should return null when no version found`() {
            val content = "no version here"

            parser.extractPlayerVersion(content).shouldBeNull()
        }
    }

    // Helper function to create mock HTML with player response
    private fun createMockHtml(
        videoId: String = "testVideoId1",
        title: String = "Test Video Title",
        author: String = "Test Author",
        lengthSeconds: String = "300",
        viewCount: String = "1000000",
        status: String = "OK",
        reason: String? = null,
        includeThumbnails: Boolean = false
    ): String {
        val thumbnailJson = if (includeThumbnails) """
            "thumbnail": {
                "thumbnails": [
                    {"url": "https://i.ytimg.com/vi/$videoId/default.jpg", "width": 120, "height": 90},
                    {"url": "https://i.ytimg.com/vi/$videoId/hqdefault.jpg", "width": 480, "height": 360}
                ]
            }
        """ else ""

        val reasonJson = if (reason != null) """, "reason": "$reason"""" else ""

        return """
            <!DOCTYPE html>
            <html>
            <head><title>$title - YouTube</title></head>
            <body>
            <script>
                var ytInitialPlayerResponse = {
                    "playabilityStatus": {
                        "status": "$status"$reasonJson
                    },
                    "videoDetails": {
                        "videoId": "$videoId",
                        "title": "$title",
                        "author": "$author",
                        "lengthSeconds": "$lengthSeconds",
                        "viewCount": "$viewCount"${if (includeThumbnails) ",$thumbnailJson" else ""}
                    },
                    "microformat": {
                        "playerMicroformatRenderer": {
                            "uploadDate": "2023-01-15",
                            "ownerChannelName": "$author"
                        }
                    }
                };
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}

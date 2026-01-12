package com.github.kotlintubeexplode.internal.dto

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Internal DTO for parsing closed caption track XML responses.
 */
internal data class ClosedCaptionTrackResponseDto(
    val captions: List<CaptionData>
) {
    data class CaptionData(
        val text: String?,
        val offset: Duration?,
        val duration: Duration?,
        val parts: List<PartData>
    )

    data class PartData(
        val text: String?,
        val offset: Duration
    )

    companion object {
        /**
         * Parses the XML response from YouTube's caption track endpoint.
         *
         * Expected format:
         * ```xml
         * <transcript>
         *   <text start="0" dur="5.5">Caption text</text>
         *   <text start="5.5" dur="3.2">
         *     <s t="0">Word1</s>
         *     <s t="500">Word2</s>
         *   </text>
         * </transcript>
         * ```
         *
         * Or newer format:
         * ```xml
         * <timedtext>
         *   <body>
         *     <p t="0" d="5500">Caption text</p>
         *   </body>
         * </timedtext>
         * ```
         */
        fun parse(xml: String): ClosedCaptionTrackResponseDto {
            val captions = mutableListOf<CaptionData>()

            // Try to parse both formats

            // Format 1: <transcript><text>...</text></transcript>
            val textPattern = Regex("""<text[^>]*\sstart="([^"]*)"[^>]*\sdur="([^"]*)"[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)

            // Format 2: <p t="..." d="...">...</p> (timedtext format)
            val pPattern = Regex("""<p[^>]*\st="(\d+)"[^>]*\sd="(\d+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

            // Try format 1 first
            var matches = textPattern.findAll(xml).toList()
            if (matches.isEmpty()) {
                // Try format 2
                matches = pPattern.findAll(xml).toList()
            }

            for (match in matches) {
                val (startStr, durStr, content) = match.destructured

                val offset = parseTime(startStr)
                val duration = parseTime(durStr)

                // Parse parts (word-level timing)
                val parts = mutableListOf<PartData>()
                val partPattern = Regex("""<s[^>]*\s(?:t|ac)="(\d+)"[^>]*>(.*?)</s>""", RegexOption.DOT_MATCHES_ALL)
                for (partMatch in partPattern.findAll(content)) {
                    val (partOffsetStr, partText) = partMatch.destructured
                    val partOffset = partOffsetStr.toLongOrNull()?.milliseconds ?: Duration.ZERO
                    val decodedPartText = decodeHtmlEntities(partText.trim())
                    if (decodedPartText.isNotEmpty()) {
                        parts.add(PartData(decodedPartText, partOffset))
                    }
                }

                // Extract text (remove nested tags)
                val text = decodeHtmlEntities(content.replace(Regex("<[^>]+>"), "").trim())

                captions.add(CaptionData(
                    text = text.ifEmpty { null },
                    offset = offset,
                    duration = duration,
                    parts = parts
                ))
            }

            return ClosedCaptionTrackResponseDto(captions)
        }

        private fun parseTime(value: String): Duration? {
            // Handle both seconds format (5.5) and milliseconds format (5500)
            return when {
                value.contains(".") -> {
                    // Seconds format: 5.5 -> 5500ms
                    val seconds = value.toDoubleOrNull() ?: return null
                    (seconds * 1000).toLong().milliseconds
                }
                else -> {
                    // Milliseconds format: 5500 -> 5500ms
                    value.toLongOrNull()?.milliseconds
                }
            }
        }

        private fun decodeHtmlEntities(text: String): String {
            return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&#x27;", "'")
                .replace("&#x2F;", "/")
                .replace("&nbsp;", " ")
                .replace(Regex("&#(\\d+);")) { match ->
                    val code = match.groupValues[1].toIntOrNull()
                    code?.toChar()?.toString() ?: match.value
                }
                .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                    val code = match.groupValues[1].toIntOrNull(16)
                    code?.toChar()?.toString() ?: match.value
                }
        }
    }
}

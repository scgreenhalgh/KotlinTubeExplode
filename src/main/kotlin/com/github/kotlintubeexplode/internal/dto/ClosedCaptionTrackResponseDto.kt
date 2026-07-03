package com.github.kotlintubeexplode.internal.dto

import com.github.kotlintubeexplode.internal.parseXmlSecurely
import org.w3c.dom.Element
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
         * <timedtext>
         *   <body>
         *     <p t="0" d="5500">Caption text</p>
         *     <p t="5500" d="3200">
         *       <s t="0">Word1</s>
         *       <s t="500">Word2</s>
         *     </p>
         *   </body>
         * </timedtext>
         * ```
         *
         * Walks the parsed XML tree (mirroring upstream's XLinq approach in
         * Bridge/ClosedCaptionTrackResponse.cs) rather than pattern-matching the raw
         * string, so attribute order, self-closing elements, and nested markup are all
         * handled by the XML parser.
         */
        fun parse(xml: String): ClosedCaptionTrackResponseDto {
            // A blank body (e.g. an unexpected empty 200) has no captions; don't hand it
            // to the XML parser, which would throw on premature end-of-input.
            if (xml.isBlank()) return ClosedCaptionTrackResponseDto(emptyList())

            val document = parseXmlSecurely(xml)

            val captions = mutableListOf<CaptionData>()

            // Upstream: content.Descendants("p") — every <p> element, at any depth.
            val pNodes = document.getElementsByTagName("p")
            for (i in 0 until pNodes.length) {
                val p = pNodes.item(i) as? Element ?: continue

                captions.add(
                    CaptionData(
                        // (string?)content: concatenation of all descendant text, with entities
                        // already decoded by the XML parser. Deliberately NOT trimmed —
                        // whitespace-only captions are meaningful and must survive
                        // (https://github.com/Tyrrrz/YoutubeExplode/issues/671).
                        text = p.textContent,
                        offset = p.attributeMillis("t"),
                        duration = p.attributeMillis("d"),
                        parts = parseParts(p)
                    )
                )
            }

            return ClosedCaptionTrackResponseDto(captions)
        }

        /**
         * Word-level parts. Upstream uses `content.Elements("s")` — direct child `<s>`
         * elements only, not descendants.
         */
        private fun parseParts(p: Element): List<PartData> {
            val parts = mutableListOf<PartData>()
            val children = p.childNodes
            for (j in 0 until children.length) {
                val s = children.item(j) as? Element ?: continue
                if (s.tagName != "s") continue

                // Upstream: t ?? ac ?? TimeSpan.Zero
                val offset = s.attributeMillis("t")
                    ?: s.attributeMillis("ac")
                    ?: Duration.ZERO

                parts.add(PartData(s.textContent, offset))
            }
            return parts
        }

        /**
         * Reads an attribute as a millisecond [Duration], or null when absent/non-numeric.
         * Missing attributes come back as "" from the DOM, which parses to null.
         */
        private fun Element.attributeMillis(name: String): Duration? =
            getAttribute(name).toDoubleOrNull()?.milliseconds
    }
}

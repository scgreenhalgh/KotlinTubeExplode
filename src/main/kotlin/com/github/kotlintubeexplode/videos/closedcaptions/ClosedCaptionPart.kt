package com.github.kotlintubeexplode.videos.closedcaptions

import kotlin.time.Duration

/**
 * Individual closed caption part contained within a caption.
 * Parts usually represent individual words.
 *
 * @param text Text displayed by the caption part
 * @param offset Time at which the caption part starts displaying, relative to the caption's own offset
 */
data class ClosedCaptionPart(
    val text: String,
    val offset: Duration
) {
    override fun toString(): String = text
}

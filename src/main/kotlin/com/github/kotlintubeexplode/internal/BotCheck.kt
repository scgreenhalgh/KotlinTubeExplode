package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.BotCheckException
import com.github.kotlintubeexplode.exceptions.VideoUnavailableException
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto

/**
 * True when we got at least one response and every one was YouTube's bot check. Then nobody
 * actually told us anything about the video, so "unavailable", "not live" or "no captions" would
 * be a guess.
 */
internal fun List<PlayerResponseDto>.allBotChecked(): Boolean =
    isNotEmpty() && all { it.playabilityStatus?.isBotCheck == true }

/**
 * The exception for a video that none of [responses] saw as available.
 *
 * If every response was a bot check it's a [BotCheckException]. Otherwise at least one client
 * answered for the video, so it's a plain [VideoUnavailableException], and that answer's reason
 * ("This video is private") goes in the message ahead of a bot check's.
 */
internal fun unavailableVideoException(
    videoId: VideoId,
    responses: List<PlayerResponseDto>
): VideoUnavailableException {
    val statuses = responses.mapNotNull { it.playabilityStatus }
    val reason = statuses.firstNotNullOfOrNull { status -> status.reason.takeUnless { status.isBotCheck } }
        ?: statuses.firstNotNullOfOrNull { it.reason }
        ?: "Unknown error"
    val message = "Video '${videoId.value}' is unavailable: $reason"
    return if (responses.allBotChecked()) BotCheckException(message) else VideoUnavailableException(message)
}

package com.github.kotlintubeexplode.exceptions

import com.github.kotlintubeexplode.core.VideoId

/**
 * Base exception thrown within KotlinTubeExplode.
 */
open class KotlinTubeExplodeException(message: String) : Exception(message)

/**
 * Exception thrown when the requested video is unplayable.
 */
open class VideoUnplayableException(message: String) : KotlinTubeExplodeException(message)

/**
 * Exception thrown when the requested video is unavailable.
 */
open class VideoUnavailableException(message: String) : VideoUnplayableException(message)

/**
 * Exception thrown when YouTube's bot check ("Sign in to confirm you're not a bot") blocked
 * every client we asked, so we can't tell whether the video exists.
 *
 * It's about the network you're on, not the video: retrying straight away won't help. Try again
 * later, from another network, or with cookies from a signed-in session. It extends
 * [VideoUnavailableException], so existing `catch` blocks still see it.
 */
class BotCheckException(message: String) : VideoUnavailableException(message)

/**
 * Exception thrown when the requested video requires purchase.
 *
 * @param previewVideoId ID of a free preview video used as promotion for the original video
 */
class VideoRequiresPurchaseException(
    message: String,
    val previewVideoId: VideoId
) : VideoUnplayableException(message)

/**
 * Exception thrown when YouTube denies a request because the client has exceeded rate limit.
 */
class RequestLimitExceededException(message: String) : KotlinTubeExplodeException(message)

/**
 * Exception thrown when the requested playlist is unavailable.
 */
class PlaylistUnavailableException(message: String) : KotlinTubeExplodeException(message)

/**
 * Exception thrown when the requested channel is unavailable.
 */
class ChannelUnavailableException(message: String) : KotlinTubeExplodeException(message)

/**
 * Exception thrown when a closed caption track is unavailable.
 */
class ClosedCaptionTrackUnavailableException(message: String) : KotlinTubeExplodeException(message)

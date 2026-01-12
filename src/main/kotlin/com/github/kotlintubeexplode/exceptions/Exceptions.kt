package com.github.kotlintubeexplode.exceptions

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
class VideoUnavailableException(message: String) : VideoUnplayableException(message)

/**
 * Exception thrown when the requested video requires purchase.
 *
 * @param previewVideoId ID of a free preview video used as promotion for the original video
 */
class VideoRequiresPurchaseException(
    message: String,
    val previewVideoId: String
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

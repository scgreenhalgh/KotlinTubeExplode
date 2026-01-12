package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Language
import com.github.kotlintubeexplode.common.Resolution

/**
 * Base interface for all stream types.
 */
sealed interface IStreamInfo {
    /** The stream URL. */
    val url: String

    /** The container format. */
    val container: Container

    /** The file size. */
    val size: FileSize

    /** The bitrate. */
    val bitrate: Bitrate

    /**
     * Returns true if this stream is throttled (lacks "ratebypass=yes" parameter).
     */
    val isThrottled: Boolean
        get() = !url.contains("ratebypass=yes", ignoreCase = true)
}

/**
 * Interface for streams that contain audio.
 */
interface IAudioStreamInfo : IStreamInfo {
    /** The audio codec name (e.g., "mp4a.40.2", "opus"). */
    val audioCodec: String

    /** The audio language, or null if unknown. */
    val audioLanguage: Language?

    /** Whether this is the default audio language, or null if unknown. */
    val isAudioLanguageDefault: Boolean?
}

/**
 * Interface for streams that contain video.
 */
interface IVideoStreamInfo : IStreamInfo {
    /** The video codec name (e.g., "avc1.4d401f", "vp9"). */
    val videoCodec: String

    /** The video quality information. */
    val videoQuality: VideoQuality

    /** The video resolution in pixels. */
    val videoResolution: Resolution
}

/**
 * Represents an audio-only stream.
 */
data class AudioOnlyStreamInfo(
    override val url: String,
    override val container: Container,
    override val size: FileSize,
    override val bitrate: Bitrate,
    override val audioCodec: String,
    override val audioLanguage: Language? = null,
    override val isAudioLanguageDefault: Boolean? = null
) : IAudioStreamInfo {
    override fun toString(): String =
        if (audioLanguage != null) "Audio-only ($container | $audioLanguage)"
        else "Audio-only ($container)"
}

/**
 * Represents a video-only stream.
 */
data class VideoOnlyStreamInfo(
    override val url: String,
    override val container: Container,
    override val size: FileSize,
    override val bitrate: Bitrate,
    override val videoCodec: String,
    override val videoQuality: VideoQuality,
    override val videoResolution: Resolution
) : IVideoStreamInfo {
    override fun toString(): String = "Video-only ($videoQuality | $container)"
}

/**
 * Represents a muxed stream (contains both audio and video).
 */
data class MuxedStreamInfo(
    override val url: String,
    override val container: Container,
    override val size: FileSize,
    override val bitrate: Bitrate,
    override val audioCodec: String,
    override val audioLanguage: Language? = null,
    override val isAudioLanguageDefault: Boolean? = null,
    override val videoCodec: String,
    override val videoQuality: VideoQuality,
    override val videoResolution: Resolution
) : IAudioStreamInfo, IVideoStreamInfo {
    override fun toString(): String = "Muxed ($videoQuality | $container)"
}

/**
 * Extension function to get the stream with the highest bitrate.
 */
fun <T : IStreamInfo> Iterable<T>.getWithHighestBitrate(): T? =
    maxByOrNull { it.bitrate }

/**
 * Extension function to get the video stream with the highest quality.
 */
fun <T : IVideoStreamInfo> Iterable<T>.getWithHighestVideoQuality(): T? =
    maxByOrNull { it.videoQuality }

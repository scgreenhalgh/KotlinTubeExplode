package com.github.kotlintubeexplode.videos.streams

/**
 * Represents a manifest containing all available streams for a video.
 */
data class StreamManifest(
    /**
     * All available streams.
     */
    val streams: List<IStreamInfo>
) {
    /**
     * Gets all streams that contain audio (muxed + audio-only).
     */
    fun getAudioStreams(): List<IAudioStreamInfo> =
        streams.filterIsInstance<IAudioStreamInfo>()

    /**
     * Gets all streams that contain video (muxed + video-only).
     */
    fun getVideoStreams(): List<IVideoStreamInfo> =
        streams.filterIsInstance<IVideoStreamInfo>()

    /**
     * Gets only muxed streams (contain both audio and video).
     */
    fun getMuxedStreams(): List<MuxedStreamInfo> =
        streams.filterIsInstance<MuxedStreamInfo>()

    /**
     * Gets only audio-only streams.
     */
    fun getAudioOnlyStreams(): List<AudioOnlyStreamInfo> =
        streams.filterIsInstance<AudioOnlyStreamInfo>()

    /**
     * Gets only video-only streams.
     */
    fun getVideoOnlyStreams(): List<VideoOnlyStreamInfo> =
        streams.filterIsInstance<VideoOnlyStreamInfo>()

    /**
     * Gets the muxed stream with the highest video quality.
     */
    fun getBestMuxedStream(): MuxedStreamInfo? =
        getMuxedStreams().getWithHighestVideoQuality()

    /**
     * Gets the audio stream with the highest bitrate.
     */
    fun getBestAudioStream(): IAudioStreamInfo? =
        getAudioStreams().getWithHighestBitrate()

    /**
     * Gets the video stream with the highest quality.
     */
    fun getBestVideoStream(): IVideoStreamInfo? =
        getVideoStreams().getWithHighestVideoQuality()
}

package com.github.kotlintubeexplode.client

import com.github.kotlintubeexplode.channels.ChannelClient
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.playlists.PlaylistClient
import com.github.kotlintubeexplode.search.SearchClient
import com.github.kotlintubeexplode.videos.Video
import com.github.kotlintubeexplode.videos.VideoClient
import com.github.kotlintubeexplode.videos.closedcaptions.ClosedCaptionClient
import com.github.kotlintubeexplode.videos.streams.StreamClient
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Main entry point for the KotlinTubeExplode library.
 *
 * Provides a clean, coroutine-based API for extracting YouTube video metadata,
 * playlists, channels, search results, and stream information.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val youtube = YoutubeClient()
 *
 * // Get video metadata
 * val video = youtube.videos.get("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
 * println("Title: ${video.title}")
 * println("Author: ${video.author.channelTitle}")
 * println("Duration: ${video.duration}")
 *
 * // Get stream manifest
 * val manifest = youtube.streams.getManifest(video.id)
 * val bestAudio = manifest.getBestAudioStream()
 *
 * // Get playlist videos
 * val videos = youtube.playlists.getVideos("PLxxxxxx").toList()
 *
 * // Search for videos
 * val results = youtube.search.getVideos("kotlin tutorial").take(10).toList()
 *
 * // Get channel info
 * val channel = youtube.channels.getByHandle("@GoogleDevelopers")
 * ```
 *
 * ## Thread Safety
 *
 * This client is thread-safe and can be used from multiple coroutines concurrently.
 * It is recommended to create a single instance and reuse it.
 *
 * ## Custom HTTP Client
 *
 * You can provide your own OkHttpClient for custom configuration:
 *
 * ```kotlin
 * val customClient = OkHttpClient.Builder()
 *     .proxy(myProxy)
 *     .build()
 *
 * val youtube = YoutubeClient(customClient)
 * ```
 *
 * @param httpClient Optional custom OkHttpClient. If not provided, a default client is created.
 */
class YoutubeClient(
    httpClient: OkHttpClient? = null
) : Closeable {
    /**
     * The OkHttpClient instance used for HTTP requests.
     * If no client was provided, we create our own that we'll dispose on close().
     */
    private val ownedClient: OkHttpClient? = if (httpClient == null) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    } else {
        null // User provided their own client, we don't own it
    }

    private val httpController: HttpController = HttpController(httpClient ?: ownedClient!!)

    private val videoController: VideoController = VideoController(httpController)

    /**
     * Client for retrieving video information.
     *
     * ```kotlin
     * val video = youtube.videos.get("dQw4w9WgXcQ")
     * ```
     */
    val videos: VideoClient = VideoClient(videoController)

    /**
     * Client for retrieving stream manifests and downloading media.
     *
     * ```kotlin
     * val manifest = youtube.streams.getManifest("dQw4w9WgXcQ")
     * val bestVideo = manifest.getBestVideoStream()
     * youtube.streams.download(bestVideo, "video.mp4")
     * ```
     */
    val streams: StreamClient = StreamClient(httpController, videoController)

    /**
     * Client for retrieving playlist information and videos.
     *
     * ```kotlin
     * val playlist = youtube.playlists.get("PLxxxxxx")
     * val videos = youtube.playlists.getVideos(playlist.id).toList()
     * ```
     */
    val playlists: PlaylistClient = PlaylistClient(httpController)

    /**
     * Client for retrieving channel information.
     *
     * ```kotlin
     * val channel = youtube.channels.get("UCxxxxxx")
     * val byHandle = youtube.channels.getByHandle("@username")
     * val uploads = youtube.channels.getUploads(channel.id).toList()
     * ```
     */
    val channels: ChannelClient = ChannelClient(httpController, playlists)

    /**
     * Client for searching YouTube.
     *
     * ```kotlin
     * val videos = youtube.search.getVideos("kotlin tutorial")
     * val channels = youtube.search.getChannels("google developers")
     * val playlists = youtube.search.getPlaylists("kotlin course")
     * ```
     */
    val search: SearchClient = SearchClient(httpController)

    /**
     * Client for retrieving closed captions (subtitles).
     *
     * ```kotlin
     * val manifest = youtube.closedCaptions.getManifest("dQw4w9WgXcQ")
     * val englishTrack = manifest.getByLanguage("en")
     * val track = youtube.closedCaptions.get(englishTrack)
     * youtube.closedCaptions.downloadSrt(englishTrack, "captions.srt")
     * ```
     */
    val closedCaptions: ClosedCaptionClient = ClosedCaptionClient(httpController, videoController)

    // ============================================
    // Convenience methods for backward compatibility
    // ============================================

    /**
     * Retrieves metadata for a YouTube video.
     *
     * This is a convenience method equivalent to `videos.get(url)`.
     *
     * @param url The video URL or ID. Supports:
     *   - Raw video IDs: `dQw4w9WgXcQ`
     *   - Full URLs: `https://www.youtube.com/watch?v=dQw4w9WgXcQ`
     *   - Short URLs: `https://youtu.be/dQw4w9WgXcQ`
     *   - Embed URLs: `https://www.youtube.com/embed/dQw4w9WgXcQ`
     *   - Shorts URLs: `https://www.youtube.com/shorts/dQw4w9WgXcQ`
     *
     * @return The video metadata
     * @throws IllegalArgumentException if the URL/ID is invalid
     * @throws VideoUnavailableException if the video is not available
     * @throws VideoParseException if parsing fails
     */
    suspend fun getVideo(url: String): Video {
        return videos.get(url)
    }

    /**
     * Retrieves metadata for a YouTube video by VideoId.
     *
     * This is a convenience method equivalent to `videos.get(videoId)`.
     *
     * @param videoId The validated video ID
     * @return The video metadata
     * @throws VideoUnavailableException if the video is not available
     * @throws VideoParseException if parsing fails
     */
    suspend fun getVideo(videoId: VideoId): Video {
        return videos.get(videoId)
    }

    /**
     * Checks if a video exists and is available.
     *
     * @param url The video URL or ID
     * @return true if the video exists and is available
     */
    suspend fun isVideoAvailable(url: String): Boolean {
        return try {
            getVideo(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Releases resources used by this client.
     *
     * If a custom OkHttpClient was provided in the constructor, it will NOT be closed
     * (the caller retains ownership). Only the internally-created client is closed.
     *
     * After calling close(), this client should not be used.
     */
    override fun close() {
        ownedClient?.let { client ->
            // Shut down the dispatcher's executor service
            client.dispatcher.executorService.shutdown()
            // Evict all connections from the connection pool
            client.connectionPool.evictAll()
            // Close the cache if present
            client.cache?.close()
        }
    }

    companion object {
        /**
         * Library version.
         */
        const val VERSION = "1.0.0-SNAPSHOT"

        /**
         * Creates a new YoutubeClient with default settings.
         */
        fun create(): YoutubeClient = YoutubeClient()
    }
}

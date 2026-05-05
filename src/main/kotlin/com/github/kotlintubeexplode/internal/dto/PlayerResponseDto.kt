package com.github.kotlintubeexplode.internal.dto

import com.github.kotlintubeexplode.internal.Protobuf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO for YouTube's player API response.
 *
 * This represents the structure of `ytInitialPlayerResponse` embedded in watch pages
 * or returned from the `youtubei/v1/player` API endpoint.
 *
 * Uses kotlinx.serialization with ignoreUnknownKeys for forward compatibility.
 */
@Serializable
data class PlayerResponseDto(
    @SerialName("playabilityStatus")
    val playabilityStatus: PlayabilityStatusDto? = null,

    @SerialName("videoDetails")
    val videoDetails: VideoDetailsDto? = null,

    @SerialName("microformat")
    val microformat: MicroformatDto? = null,

    @SerialName("streamingData")
    val streamingData: StreamingDataDto? = null,

    @SerialName("captions")
    val captions: CaptionsDto? = null
) {
    /**
     * Returns true if metadata can be extracted from this response.
     *
     * Upstream-equivalent of `PlayerResponse.IsAvailable`. Distinct from
     * `playabilityStatus.isPlayable`: a video can be unavailable for streaming
     * (LOGIN_REQUIRED, CONTENT_CHECK_REQUIRED, AGE_VERIFICATION_REQUIRED, etc.)
     * but still expose its metadata.
     */
    val isAvailable: Boolean
        get() = playabilityStatus?.status?.equals("error", ignoreCase = true) != true &&
                videoDetails != null
}

/**
 * Playability status - indicates if the video is available.
 */
@Serializable
data class PlayabilityStatusDto(
    @SerialName("status")
    val status: String? = null,

    @SerialName("reason")
    val reason: String? = null,

    @SerialName("playableInEmbed")
    val playableInEmbed: Boolean? = null,

    @SerialName("liveStreamability")
    val liveStreamability: JsonObject? = null
) {
    /**
     * Returns true if the video is playable (streams can be extracted).
     *
     * Upstream uses ordinal-ignore-case comparison; mirror that here so a
     * lowercase "ok" from YouTube doesn't silently break stream extraction.
     */
    val isPlayable: Boolean
        get() = status?.equals("OK", ignoreCase = true) == true

    /**
     * Returns true if this is a live stream.
     */
    val isLiveStream: Boolean
        get() = liveStreamability != null

    /**
     * Returns true if the video is age-restricted.
     *
     * Age-restricted videos typically return:
     * - status: "LOGIN_REQUIRED" or "CONTENT_CHECK_REQUIRED" or "AGE_VERIFICATION_REQUIRED"
     * - Or reason containing the specific phrase "Sign in to confirm your age"
     *
     * Used to determine whether to fall back to the TV Embedded client.
     */
    val isAgeRestricted: Boolean
        get() = status?.equals("LOGIN_REQUIRED", ignoreCase = true) == true ||
                status?.equals("CONTENT_CHECK_REQUIRED", ignoreCase = true) == true ||
                status?.equals("AGE_VERIFICATION_REQUIRED", ignoreCase = true) == true ||
                reason?.contains("Sign in to confirm your age", ignoreCase = true) == true
}

/**
 * Video details - core metadata.
 */
@Serializable
data class VideoDetailsDto(
    @SerialName("videoId")
    val videoId: String? = null,

    @SerialName("title")
    val title: String? = null,

    @SerialName("lengthSeconds")
    val lengthSeconds: String? = null,

    @SerialName("keywords")
    val keywords: List<String>? = null,

    @SerialName("channelId")
    val channelId: String? = null,

    @SerialName("shortDescription")
    val shortDescription: String? = null,

    @SerialName("viewCount")
    val viewCount: String? = null,

    @SerialName("author")
    val author: String? = null,

    @SerialName("isLiveContent")
    val isLiveContent: Boolean? = null,

    @SerialName("thumbnail")
    val thumbnail: ThumbnailContainerDto? = null
) {
    /**
     * Duration in seconds, or null for live streams.
     */
    val durationSeconds: Long?
        get() = lengthSeconds?.toLongOrNull()?.takeIf { it > 0 }

    /**
     * View count as Long.
     */
    val viewCountLong: Long?
        get() = viewCount?.toLongOrNull()
}

/**
 * Container for thumbnail list.
 */
@Serializable
data class ThumbnailContainerDto(
    @SerialName("thumbnails")
    val thumbnails: List<ThumbnailDto>? = null
)

/**
 * Individual thumbnail data.
 */
@Serializable
data class ThumbnailDto(
    @SerialName("url")
    val url: String? = null,

    @SerialName("width")
    val width: Int? = null,

    @SerialName("height")
    val height: Int? = null
)

/**
 * Microformat contains additional metadata like upload date.
 */
@Serializable
data class MicroformatDto(
    @SerialName("playerMicroformatRenderer")
    val playerMicroformatRenderer: PlayerMicroformatRendererDto? = null
)

@Serializable
data class PlayerMicroformatRendererDto(
    @SerialName("uploadDate")
    val uploadDate: String? = null,

    @SerialName("publishDate")
    val publishDate: String? = null,

    @SerialName("lengthSeconds")
    val lengthSeconds: String? = null,

    @SerialName("ownerChannelName")
    val ownerChannelName: String? = null,

    @SerialName("viewCount")
    val viewCount: String? = null
)

/**
 * Streaming data - contains format/stream information.
 */
@Serializable
data class StreamingDataDto(
    @SerialName("formats")
    val formats: List<StreamFormatDto>? = null,

    @SerialName("adaptiveFormats")
    val adaptiveFormats: List<StreamFormatDto>? = null,

    @SerialName("dashManifestUrl")
    val dashManifestUrl: String? = null,

    @SerialName("hlsManifestUrl")
    val hlsManifestUrl: String? = null,

    @SerialName("expiresInSeconds")
    val expiresInSeconds: String? = null
) {
    /**
     * All available formats (muxed + adaptive).
     */
    val allFormats: List<StreamFormatDto>
        get() = (formats.orEmpty() + adaptiveFormats.orEmpty())
}

/**
 * Individual stream format.
 */
@Serializable
data class StreamFormatDto(
    @SerialName("itag")
    val itag: Int? = null,

    @SerialName("url")
    val url: String? = null,

    @SerialName("mimeType")
    val mimeType: String? = null,

    @SerialName("bitrate")
    val bitrate: Long? = null,

    @SerialName("width")
    val width: Int? = null,

    @SerialName("height")
    val height: Int? = null,

    @SerialName("contentLength")
    val contentLength: String? = null,

    @SerialName("quality")
    val quality: String? = null,

    @SerialName("qualityLabel")
    val qualityLabel: String? = null,

    @SerialName("fps")
    val fps: Int? = null,

    @SerialName("audioQuality")
    val audioQuality: String? = null,

    @SerialName("audioSampleRate")
    val audioSampleRate: String? = null,

    @SerialName("audioChannels")
    val audioChannels: Int? = null,

    // For encrypted streams
    @SerialName("signatureCipher")
    val signatureCipher: String? = null,

    @SerialName("cipher")
    val cipher: String? = null,

    // Audio track info (for videos with multiple audio languages)
    @SerialName("audioTrack")
    val audioTrack: AudioTrackDto? = null,

    // Base64-encoded protobuf map<string,string>. {"sr":"1"} means Super Resolution upscaling.
    @SerialName("xtags")
    val xtags: String? = null
) {
    /**
     * Returns true if this stream was produced by YouTube's Super Resolution
     * (AI upscaling). Detected via the `sr=1` entry in the `xtags` protobuf map.
     */
    val isVideoUpscaled: Boolean
        get() = xtags?.takeIf { it.isNotBlank() }
            ?.let(Protobuf::tryDeserializeMap)
            ?.get("sr") == "1"
    /**
     * Returns true if this format requires signature decryption.
     */
    val requiresDecryption: Boolean
        get() = url == null && (signatureCipher != null || cipher != null)

    /**
     * Gets the cipher data string (either signatureCipher or cipher field).
     */
    val cipherData: String?
        get() = signatureCipher ?: cipher

    /**
     * Returns true if this is an audio-only format.
     */
    val isAudioOnly: Boolean
        get() = mimeType?.startsWith("audio/") == true

    /**
     * Returns true if this is a video-only format.
     */
    val isVideoOnly: Boolean
        get() = mimeType?.startsWith("video/") == true && audioQuality == null

    /**
     * Content length as Long.
     */
    val contentLengthLong: Long?
        get() = contentLength?.toLongOrNull()

    /**
     * Extracts the container format (mp4, webm, etc.) from mimeType.
     */
    val container: String?
        get() = mimeType?.substringBefore(";")?.substringAfter("/")

    /**
     * Extracts the codec string from mimeType.
     */
    val codecs: String?
        get() = mimeType?.let {
            val start = it.indexOf("codecs=\"")
            if (start == -1) return@let null
            val end = it.indexOf("\"", start + 8)
            if (end == -1) return@let null
            it.substring(start + 8, end)
        }

    /**
     * Extracts the video codec from the codecs string.
     * For muxed streams, video codec comes first (before comma).
     *
     * YouTube returns the literal string "unknown" for some AV1 streams; map to
     * the canonical AV1 codec string per upstream behavior.
     */
    val videoCodec: String?
        get() = if (isAudioOnly) {
            null
        } else {
            val raw = codecs?.substringBefore(",")?.trim()
            if (raw?.equals("unknown", ignoreCase = true) == true) "av01.0.05M.08" else raw
        }

    /**
     * Extracts the audio codec from the codecs string.
     * For muxed streams, audio codec comes second (after comma).
     * For audio-only streams, it's the entire codecs string.
     */
    val audioCodec: String?
        get() = when {
            isAudioOnly -> codecs
            codecs?.contains(",") == true -> codecs?.substringAfter(",")?.trim()
            else -> null
        }

    /**
     * Audio language code (e.g., "en", "es", "fr").
     */
    val audioLanguageCode: String?
        get() = audioTrack?.id?.substringBefore(".")

    /**
     * Audio language display name.
     */
    val audioLanguageName: String?
        get() = audioTrack?.displayName

    /**
     * Returns true if this is the default audio track.
     */
    val isDefaultAudioTrack: Boolean
        get() = audioTrack?.audioIsDefault == true
}

/**
 * Audio track information for streams with multiple audio languages.
 */
@Serializable
data class AudioTrackDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("displayName")
    val displayName: String? = null,

    @SerialName("audioIsDefault")
    val audioIsDefault: Boolean? = null
)

// ============================================================================
// Closed Captions DTOs
// ============================================================================

/**
 * Container for closed caption data.
 */
@Serializable
data class CaptionsDto(
    @SerialName("playerCaptionsTracklistRenderer")
    val playerCaptionsTracklistRenderer: PlayerCaptionsTracklistRendererDto? = null
)

@Serializable
data class PlayerCaptionsTracklistRendererDto(
    @SerialName("captionTracks")
    val captionTracks: List<CaptionTrackDto>? = null,

    @SerialName("translationLanguages")
    val translationLanguages: List<TranslationLanguageDto>? = null
)

/**
 * Individual closed caption track.
 */
@Serializable
data class CaptionTrackDto(
    @SerialName("baseUrl")
    val baseUrl: String? = null,

    @SerialName("name")
    val name: CaptionNameDto? = null,

    @SerialName("vssId")
    val vssId: String? = null,

    @SerialName("languageCode")
    val languageCode: String? = null,

    @SerialName("kind")
    val kind: String? = null,

    @SerialName("isTranslatable")
    val isTranslatable: Boolean? = null
) {
    /**
     * Returns the display name of the caption track.
     */
    val displayName: String?
        get() = name?.simpleText ?: name?.runs?.firstOrNull()?.text

    /**
     * Returns true if this is an auto-generated caption track.
     * Auto-generated tracks have vssId starting with "a."
     */
    val isAutoGenerated: Boolean
        get() = vssId?.startsWith("a.") == true
}

@Serializable
data class CaptionNameDto(
    @SerialName("simpleText")
    val simpleText: String? = null,

    @SerialName("runs")
    val runs: List<TextRunDto>? = null
)

/**
 * Available translation language.
 */
@Serializable
data class TranslationLanguageDto(
    @SerialName("languageCode")
    val languageCode: String? = null,

    @SerialName("languageName")
    val languageName: CaptionNameDto? = null
)

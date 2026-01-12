package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.common.Resolution
import com.github.kotlintubeexplode.videos.streams.*
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser for DASH manifests.
 *
 * DASH (Dynamic Adaptive Streaming over HTTP) manifests contain
 * information about available video and audio streams.
 */
internal class DashManifestParser {

    /**
     * Parses a DASH manifest XML and extracts stream information.
     *
     * @param manifestXml The raw DASH manifest XML content
     * @return List of stream info objects
     */
    fun parse(manifestXml: String): List<IStreamInfo> {
        val streams = mutableListOf<IStreamInfo>()

        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                // Prevent XXE (XML External Entity) attacks
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setXIncludeAware(false)
                setExpandEntityReferences(false)
            }
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(manifestXml.byteInputStream())

            val representations = document.getElementsByTagName("Representation")
            for (i in 0 until representations.length) {
                val element = representations.item(i) as? Element ?: continue

                // Skip non-numeric IDs (like "rawcc" for closed captions)
                val id = element.getAttribute("id")
                if (!id.all { it.isDigit() }) continue

                // Skip segmented streams
                val initUrl = element.getElementsByTagName("Initialization")
                    .item(0)?.let { (it as? Element)?.getAttribute("sourceURL") }
                if (initUrl?.contains("sq/") == true) continue

                // Skip streams without codecs
                val codecs = element.getAttribute("codecs").takeIf { it.isNotBlank() } ?: continue

                val streamInfo = parseRepresentation(element, codecs)
                if (streamInfo != null) {
                    streams.add(streamInfo)
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse failure
        }

        return streams
    }

    private fun parseRepresentation(element: Element, codecs: String): IStreamInfo? {
        val itag = element.getAttribute("id").toIntOrNull() ?: return null

        // Get BaseURL for stream URL
        val baseUrl = element.getElementsByTagName("BaseURL")
            .item(0)?.textContent ?: return null

        // Parse content length from URL or attribute
        val contentLength = parseContentLength(baseUrl, element)

        // Parse bitrate
        val bitrate = element.getAttribute("bandwidth").toLongOrNull() ?: 0L

        // Determine container from URL
        val container = parseContainer(baseUrl)

        // Check if audio-only (has AudioChannelConfiguration)
        val hasAudio = element.getElementsByTagName("AudioChannelConfiguration").length > 0

        // Parse video dimensions
        val width = element.getAttribute("width").toIntOrNull()
        val height = element.getAttribute("height").toIntOrNull()
        val framerate = element.getAttribute("frameRate").toIntOrNull() ?: 30

        return if (hasAudio && width == null) {
            // Audio-only stream
            AudioOnlyStreamInfo(
                url = baseUrl,
                container = container,
                size = FileSize(contentLength),
                bitrate = Bitrate(bitrate),
                audioCodec = codecs
            )
        } else if (width != null && height != null) {
            // Video stream (may or may not have audio)
            val videoQuality = VideoQuality.fromItag(itag, framerate)
            val resolution = Resolution(width, height)

            // Only treat as muxed if codecs string contains both video and audio codecs (comma-separated)
            val hasBothCodecs = hasAudio && codecs.contains(",")

            if (hasBothCodecs) {
                // Muxed stream (rare in DASH)
                val videoCodec = codecs.substringBefore(",").trim()
                val audioCodec = codecs.substringAfter(",").trim()
                MuxedStreamInfo(
                    url = baseUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    audioCodec = audioCodec,
                    videoCodec = videoCodec,
                    videoQuality = videoQuality,
                    videoResolution = resolution
                )
            } else {
                // Video-only stream (or video with audio channel config but single codec)
                VideoOnlyStreamInfo(
                    url = baseUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    videoCodec = codecs.substringBefore(",").trim(),
                    videoQuality = videoQuality,
                    videoResolution = resolution
                )
            }
        } else {
            null
        }
    }

    private fun parseContentLength(url: String, element: Element): Long {
        // Try contentLength attribute first
        element.getAttribute("contentLength").toLongOrNull()?.let { return it }

        // Try to extract from URL (clen parameter)
        Regex("""[/\?]clen[/=](\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }

        return 0L
    }

    private fun parseContainer(url: String): Container {
        // Extract from mime parameter in URL
        val mimeMatch = Regex("""mime[/=]\w*%2F([\w\d]*)""").find(url)
        if (mimeMatch != null) {
            val decoded = java.net.URLDecoder.decode(mimeMatch.groupValues[1], "UTF-8")
            return Container(decoded)
        }

        // Fallback based on URL extension
        return when {
            url.contains(".mp4") -> Container.Mp4
            url.contains(".webm") -> Container.WebM
            else -> Container.Mp4
        }
    }
}

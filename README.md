# KotlinTubeExplode

A Kotlin library for extracting YouTube video metadata, streams, playlists, channels, and captions. This is a native port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode) (C#) to Kotlin/JVM.

No API key required. Works by reverse-engineering YouTube's internal endpoints.

## Requirements

- JVM 17+
- Kotlin 1.9+

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.kotlintubeexplode:kotlintubeexplode:1.2.1")
}
```

Maven:
```xml
<dependency>
    <groupId>com.github.kotlintubeexplode</groupId>
    <artifactId>kotlintubeexplode</artifactId>
    <version>1.2.1</version>
</dependency>
```

## Usage

```kotlin
import com.github.kotlintubeexplode.client.YoutubeClient

suspend fun main() {
    val youtube = YoutubeClient()

    // Get video info
    val video = youtube.videos.get("dQw4w9WgXcQ")
    println("${video.title} by ${video.author.channelTitle}")
    println("Duration: ${video.duration}")
    println("Views: ${video.engagement.viewCount}")

    // Get streams and download
    val manifest = youtube.streams.getManifest(video.id)
    val stream = manifest.getBestMuxedStream()

    stream?.let {
        youtube.streams.download(it, "video.mp4") { progress ->
            print("\rDownloading: ${(progress * 100).toInt()}%")
        }
    }

    youtube.close()
}
```

### URL Formats

All these work:

```kotlin
youtube.videos.get("dQw4w9WgXcQ")                                  // ID
youtube.videos.get("https://www.youtube.com/watch?v=dQw4w9WgXcQ") // Full URL
youtube.videos.get("https://youtu.be/dQw4w9WgXcQ")                 // Short URL
youtube.videos.get("https://www.youtube.com/embed/dQw4w9WgXcQ")   // Embed
youtube.videos.get("https://www.youtube.com/shorts/dQw4w9WgXcQ")  // Shorts
```

### Search

```kotlin
youtube.search.getVideos("kotlin tutorial")
    .take(10)
    .collect { video ->
        println("${video.title} - ${video.url}")
    }
```

### Playlists

```kotlin
val playlist = youtube.playlists.get("PLxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
println("${playlist.title} (${playlist.count} videos)")

youtube.playlists.getVideos(playlist.id).collect { video ->
    println("${video.index}: ${video.title}")
}
```

### Channels

```kotlin
// Multiple ways to get a channel
val channel = youtube.channels.getByHandle("@GoogleDevelopers")
// or: youtube.channels.get("UCxxxxxx")
// or: youtube.channels.getByUser("GoogleDevelopers")

// Get uploads
youtube.channels.getUploads(channel.id).take(20).collect { video ->
    println(video.title)
}
```

### Closed Captions

```kotlin
val ccManifest = youtube.closedCaptions.getManifest("dQw4w9WgXcQ")
val english = ccManifest.tryGetByLanguage("en")

english?.let { track ->
    youtube.closedCaptions.downloadSrt(track, "captions.srt")
}
```

### Custom HTTP Client

```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy.example.com", 8080)))
    .build()

val youtube = YoutubeClient(client)
```

## Stream Types

The library distinguishes between:

- **Muxed streams** - Audio + video combined (usually lower quality, max 720p)
- **Audio-only streams** - Just audio (use for music, podcasts)
- **Video-only streams** - Just video (combine with audio for high quality)

```kotlin
val manifest = youtube.streams.getManifest(videoId)

// Best of each type
val bestMuxed = manifest.getBestMuxedStream()     // Combined A/V
val bestAudio = manifest.getBestAudioStream()     // Audio only
val bestVideo = manifest.getBestVideoStream()     // Video only

// Filter by type
val audioStreams = manifest.getAudioOnlyStreams()
val videoStreams = manifest.getVideoOnlyStreams()
```

## Error Handling

```kotlin
try {
    val video = youtube.videos.get(videoId)
} catch (e: VideoUnavailableException) {
    // Video doesn't exist or is private
} catch (e: VideoUnplayableException) {
    // Video exists but can't be played (age-restricted, etc.)
} catch (e: RequestLimitExceededException) {
    // Rate limited by YouTube (HTTP 429)
}
```

## How It Works

The library fetches data from YouTube's internal API endpoints, the same ones the website uses. For encrypted stream URLs (signature cipher), it downloads YouTube's player JavaScript and extracts the decryption algorithm using regex parsing - no JavaScript execution involved.

Stream downloads handle YouTube's throttling by downloading in ~10MB segments with automatic retry.

## Security

A few hardening measures are on by default:

- **Certificate pinning** - Connections to YouTube/Google hosts are pinned to the Google Trust Services roots, so a MITM with a rogue CA can't intercept traffic. Behind a corporate proxy that does SSL inspection? Pass your own `OkHttpClient` (see [Custom HTTP Client](#custom-http-client)) to opt out.
- **Trust boundary on response URLs** - Stream, manifest, and caption URLs pulled from YouTube responses are restricted to the `youtube.com` / `googlevideo.com` / `googleapis.com` host set, checked on every redirect hop. A tampered response can't make the client fetch some internal or attacker-controlled address.
- **Hardened cipher parser** - The `base.js` signature parser uses bounded patterns and a linear scan, so a crafted player script can't pin the CPU with pathological regex backtracking.
- **Resource caps** - Response bodies are capped (64 MB) and JSON parsing is depth-limited, so a malformed or hostile response can't exhaust memory or the stack.

### Downloading with untrusted names

If the filename comes from something you don't control (like a video title), use the overload that takes a directory plus a name — it sanitizes the name so it can't escape the directory:

```kotlin
import java.io.File

// fileName is sanitized: path separators become "_", so "../../etc/passwd" can't traverse out
youtube.streams.download(stream, File("downloads"), "${video.title}.mp4") { progress ->
    print("\rDownloading: ${(progress * 100).toInt()}%")
}
```

The plain `download(stream, "path/to/file.mp4")` overload cleans only the final segment — pass it trusted paths only.

## Limitations

- YouTube's internal API changes without notice. If something breaks, please open an issue.
- Rate limiting: Making too many requests too fast will get you temporarily blocked.
- Some features (like exact like counts) are no longer available from YouTube's API.

## Credits

This library is a port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode) by [Tyrrrz](https://github.com/Tyrrrz) (Oleksii Holub). The architecture, approach, and much of the reverse-engineering work comes from that project.

## License

MIT License - see [LICENSE](LICENSE) for details.

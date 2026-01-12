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
    implementation("com.github.kotlintubeexplode:kotlintubeexplode:1.0.0")
}
```

Maven:
```xml
<dependency>
    <groupId>com.github.kotlintubeexplode</groupId>
    <artifactId>kotlintubeexplode</artifactId>
    <version>1.0.0</version>
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

## Limitations

- YouTube's internal API changes without notice. If something breaks, please open an issue.
- Rate limiting: Making too many requests too fast will get you temporarily blocked.
- Some features (like exact like counts) are no longer available from YouTube's API.

## Credits

This library is a port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode) by [Tyrrrz](https://github.com/Tyrrrz) (Oleksii Holub). The architecture, approach, and much of the reverse-engineering work comes from that project.

## License

MIT License - see [LICENSE](LICENSE) for details.

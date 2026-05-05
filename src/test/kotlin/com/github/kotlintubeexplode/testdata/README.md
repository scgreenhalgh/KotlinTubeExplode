# Test Data Fixtures

Mirror of upstream YoutubeExplode's `YoutubeExplode.Tests/TestData/*.cs` files. These are real YouTube IDs covering common edge cases (private videos, deleted videos, mix playlists, upscaled streams, etc.) that we use in integration tests.

## Why mirror upstream

- Upstream maintains these IDs as YouTube changes (e.g., when a test playlist becomes unavailable, they pick a new one).
- Mirroring lets us mechanically sync new IDs as upstream adds them.
- Sharing field names (`Normal`, `Private`, `WithUpscaledStreams`, etc.) makes upstream's test patterns directly transferable to our integration tests.

## Files

| Kotlin file | Upstream source |
|---|---|
| `PlaylistIds.kt` | `YoutubeExplode.Tests/TestData/PlaylistIds.cs` |
| `VideoIds.kt` | `YoutubeExplode.Tests/TestData/VideoIds.cs` |
| `ChannelIds.kt` | `YoutubeExplode.Tests/TestData/ChannelIds.cs` |
| `ChannelHandles.kt` | `YoutubeExplode.Tests/TestData/ChannelHandles.cs` |
| `ChannelSlugs.kt` | `YoutubeExplode.Tests/TestData/ChannelSlugs.cs` |
| `UserNames.kt` | `YoutubeExplode.Tests/TestData/UserNames.cs` |

## Syncing

When syncing a new upstream release, also pull these files:

```bash
TAG=6.6
for f in PlaylistIds VideoIds ChannelIds ChannelHandles ChannelSlugs UserNames; do
  curl -s "https://raw.githubusercontent.com/Tyrrrz/YoutubeExplode/${TAG}/YoutubeExplode.Tests/TestData/${f}.cs"
done
```

Add new IDs as `const val`. Keep field names identical to upstream — both for ease of sync and so upstream's test patterns translate directly.

## Usage

```kotlin
import com.github.kotlintubeexplode.testdata.PlaylistIds
import com.github.kotlintubeexplode.testdata.VideoIds

@Test
fun `should detect upscaled streams`() = runTest {
    val manifest = client.streams.getManifest(VideoIds.WithUpscaledStreams)
    manifest.streams.shouldNotBeEmpty()
}
```

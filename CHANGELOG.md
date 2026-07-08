# Changelog

All notable changes to KotlinTubeExplode are recorded here. Versions follow [Semantic Versioning](https://semver.org/), dates are ISO 8601, and each release links to its full notes on [GitHub](https://github.com/scgreenhalgh/KotlinTubeExplode/releases).

## [1.3.1] - 2026-07-08

Resilience fix for YouTube's SABR experiment. No API changes — a drop-in from 1.3.0.

### Fixed
- **SABR-degraded responses recover automatically.** YouTube's SABR experiment intermittently returns a playable Android VR response with the adaptive formats stripped, leaving only the muxed 360p stream. The manifest builder now retries the request up to three times to recover the full format set before accepting the degraded response, so a transient degradation no longer caps you at 360p. No effect on healthy responses.

## [1.3.0] - 2026-07-08

Expands the poToken-free client fallback chain so more videos resolve, and makes made-for-kids audio work again. Additive — no public API changes, a drop-in from 1.2.x.

### Added
- **VISIONOS and iOS fallback clients.** When the primary Android VR client returns nothing usable, the manifest builder now tries the Apple Vision Pro (VISIONOS) and iOS clients before the cipher fallback. Both hand back plain stream URLs, so it's a resilience win for videos the VR client can't serve — fewer `VideoUnplayableException` dead-ends.
- **Made-for-kids audio.** Made-for-kids videos are unplayable on the VR clients and expose only video-only streams on iOS, so `getBestAudioStream()` used to return null for them. A plain Android client now recovers the legacy muxed 360p stream (H.264 + AAC), which carries the audio track — so `getBestAudioStream()` returns a usable stream for kids content. It's a 360p muxed stream (~96 kbps AAC); demux the audio track if you need audio-only.

## [1.2.1] - 2026-07-04

Security-hardening release. No breaking API changes — a drop-in upgrade from 1.2.0 (it adds two directory + filename download overloads).

### Security
- **Certificate pinning now actually applies.** `YoutubeClient()` was building its own unpinned `OkHttpClient`, so the advertised pinning never took effect. Both entry points now build through the pinned client; the pins were corrected to the current Google Trust Services roots (GTS R1–R4 plus the legacy GlobalSign Root CA) and are guarded by a live pinning test.
- **SSRF protection.** URLs taken from YouTube responses (stream, DASH manifest, caption) are restricted to the `youtube.com` / `googlevideo.com` / `googleapis.com` host set, enforced on every redirect hop — a tampered response can't redirect the client to an internal or attacker-controlled address.
- **Cipher-parser ReDoS eliminated.** The `base.js` parser uses bounded regex quantifiers and bodies plus a linear brace-scan, so a crafted player script can't pin the CPU with pathological backtracking.
- **Denial-of-service caps.** Response bodies are capped at 64 MB and recursive JSON walking is depth-bounded at 200, so an oversized or deeply nested response can't exhaust memory or the stack.

### Added
- `download(streamInfo, directory, fileName)` and `downloadSrt(trackInfo, directory, fileName)` — sanitize the filename so an untrusted video title can't escape the target directory.

## [1.2.0] - 2026-07-04

A large parity pass against upstream YoutubeExplode 6.6. No public API changes — drop-in from 1.1.x.

### Fixed
- **Closed captions work again** — the track list now comes from the ANDROID_VR client instead of the web client, whose signed caption URLs returned empty bodies.
- **Byte-exact downloads** — fixed an off-by-a-few-bytes error where Java's `-1` EOF was folded into the read position at each segment boundary.
- Dozens of stream, playlist, search, and channel edge cases now match upstream: TV-embedded fallback triggering, retry scoping, DASH frame-rate/resolution defaults, caption DOM parsing, order-agnostic channel meta-tag parsing, and more.

## [1.1.1] - 2026-05-05

Internal version-label catch-up. No behavior change from 1.1.0.

### Fixed
- The `build.gradle.kts` version field had drifted to 1.0.3; corrected so build artifacts match the tag. (JitPack overrides this label with the git tag, so consumers were never affected.)

## [1.1.0] - 2026-05-05

Tracks upstream YoutubeExplode 6.6 and clears several long-standing bugs found in a full audit and security review.

### Added
- `IVideoStreamInfo.isVideoUpscaled` — detects YouTube Super Resolution AI-upscaled streams (with a small `xtags` protobuf decoder).
- Mix playlist (`RD…`) support via upstream's `youtubei/v1/next` model.

### Fixed
- Playlist iteration no longer truncates when a whole batch is filtered out.
- Stream URLs are verified at manifest fetch, dropping stale/expired ones before download.
- AV1 codec identification, channel logo sizing, DASH error propagation, `PT0S` duration parsing, age-restriction false matches, and more.

### Security
- Unified the GET-family header pipeline so authenticated sessions don't leak inconsistent state across calls.
- Path-traversal hardening — `download()` / `downloadSrt()` sanitize the basename of caller-supplied paths.
- Range-parameter deduplication and replace-or-add cipher signature attachment.

## [1.0.3] - 2026-04-03

### Fixed
- Stream downloads failing with 403 errors due to YouTube's Proof-of-Origin token requirement — switched to the ANDROID_VR client (matches upstream 6.5.7).
- Search crashing on videos with malformed channel IDs (e.g. just `"UC"`).

## [1.0.2] - 2026-01-14

### Security
- Path-traversal protection via `sanitizeFileName()` (replaces `/ \ : * ? " < > |` with `_`).
- Download safety — directory-path checks and partial-file cleanup on failure.

## [1.0.0] - 2026-01-12

Initial release — a Kotlin/JVM port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode) with full feature parity: video metadata, stream manifests and throttle-aware downloads, playlists, channels, search, closed captions, and pure-Kotlin cipher decryption.

[1.3.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.1
[1.3.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.0
[1.2.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.2.1
[1.2.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.2.0
[1.1.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.1.1
[1.1.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.1.0
[1.0.3]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.3
[1.0.2]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.2
[1.0.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.0

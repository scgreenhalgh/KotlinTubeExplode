# Changelog

All notable changes to KotlinTubeExplode are recorded here. Versions follow [Semantic Versioning](https://semver.org/), dates are ISO 8601, and each release links to its full notes on [GitHub](https://github.com/scgreenhalgh/KotlinTubeExplode/releases).

## [1.4.0] - 2026-11-19

You can now tell YouTube's bot check apart from a video that's actually gone. Additive: existing `catch` blocks keep working.

### Added
- **`BotCheckException`.** When YouTube answers every client we try with "Sign in to confirm you're not a bot", you get `BotCheckException` instead of a plain `VideoUnavailableException`. It's about your network or IP, not the video, so show "try again later" rather than "video not found". Retrying straight away won't help, and `getManifest` doesn't retry it. It extends `VideoUnavailableException`, so code that already catches that still sees it. Put a `catch (e: BotCheckException)` above it if you want to handle it separately.

### Changed
- `streams.getManifest`, `videos.get`, `streams.getHttpLiveStreamUrl` and `closedCaptions.getManifest` throw `BotCheckException` only when every client that answered was bot-checked. If any client gives a real answer (private, not live, no captions), you get that answer as before, and its reason now goes in the message ahead of the bot check's.
- `closedCaptions.getManifest` used to return an empty manifest when every client was bot-checked, which looked like "this video has no captions". It throws `BotCheckException` now. If you call it without a `try`, add one.
- `getHttpLiveStreamUrl` throws `BotCheckException` (a `VideoUnplayableException` subclass) where it threw a plain `VideoUnplayableException` before, with the same message.

## [1.3.3] - 2026-11-19

Copes much better with slow or flaky networks. No API changes — a drop-in from 1.3.2.

### Fixed
- **Network trouble is reported as network trouble.** A timeout, dropped connection or YouTube 5xx while fetching player data used to be swallowed, so a perfectly fine video could come back as `VideoUnplayableException` ("does not contain any playable streams") or even `VideoUnavailableException` ("Sign in to confirm you're not a bot"). Those now surface as `IOException`, which `getManifest` retries first, and a rate limit surfaces as `RequestLimitExceededException`. Same for `getHttpLiveStreamUrl` and closed captions.
- **One dropped connection no longer restarts the whole manifest.** Each stream URL check retries network errors (up to 3 attempts) before giving up.
- **Fewer connections at once.** Stream URL checks are capped at 8 in flight, like upstream's dev branch, instead of firing one per format all at once.
- **Hung connects give up sooner.** The default connect timeout is 10 seconds, down from 30 (OkHttp's own default).

### Changed
- If you catch `VideoUnplayableException` to handle a failed fetch, catch `IOException` too: that's what network failures throw now. All three methods could already throw it before; it's just used consistently now.

## [1.3.2] - 2026-11-18

Gets streams working again after YouTube started bot-checking the Android VR client. No API changes — a drop-in from 1.3.1.

### Fixed
- **Streams load again for videos YouTube bot-checks on Android VR.** Android VR now gets "Sign in to confirm you're not a bot" for a lot of videos, and the manifest builder took that as `VideoUnavailableException` without ever trying the clients that still work. VISIONOS is now the primary client (same move as YoutubeExplode 6.6.2), Android VR drops to a fallback, and a video is only reported unavailable when every client agrees it is.
- **`getHttpLiveStreamUrl` works for live streams again.** It pulled the HLS URL from the web watch page, which doesn't include it anymore. It now comes from the player responses.
- **Closed captions show up again.** Caption tracks only came from Android VR, so a bot-checked video looked like it had none. They now come from the first client that has them (VISIONOS returns none, so in practice that's Android or iOS).
- **The consent cookie doesn't expire anymore.** The hardcoded `SOCS` cookie is only valid for 13 months and lapsed in November 2026. It's now generated from the current date, as in YoutubeExplode 6.6.3.

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

[1.3.3]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.3
[1.3.2]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.2
[1.3.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.1
[1.3.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.3.0
[1.2.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.2.1
[1.2.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.2.0
[1.1.1]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.1.1
[1.1.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.1.0
[1.0.3]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.3
[1.0.2]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.2
[1.0.0]: https://github.com/scgreenhalgh/KotlinTubeExplode/releases/tag/v1.0.0

package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController

/**
 * Verifies that a stream URL is fetchable and returns its content length.
 *
 * Mirrors upstream YoutubeExplode's `StreamClient.TryGetContentLengthAsync`:
 *
 *   1. If `providedContentLength` is null, HEAD the URL to read the
 *      `Content-Length` header. A 404 (or any non-2xx response) returns null.
 *   2. Verify the URL is not stale and the content length is accurate by
 *      range-fetching the last 2 bytes. If that request fails, return null —
 *      YouTube occasionally returns metadata content-lengths that don't match
 *      the actual stream byte count, or that point at expired URLs (see
 *      <https://github.com/Tyrrrz/YoutubeExplode/issues/759>).
 *
 * Returns the verified content length on success, or null if the stream
 * should be dropped from the manifest.
 *
 * @param httpController HTTP layer used for HEAD / GET requests
 * @param url the stream URL (cipher already resolved)
 * @param providedContentLength content length from the player response, or null
 */
internal suspend fun verifyStreamUrl(
    httpController: HttpController,
    url: String,
    providedContentLength: Long?
): Long? {
    val contentLength = providedContentLength
        ?: httpController.getContentLength(url)
        ?: return null

    // Range params are (contentLength - 2, contentLength - 1). Anything below 2 produces a
    // negative offset and an invalid Range header. Streams that small can't be meaningfully
    // verified by tail probe; pass the length through.
    if (contentLength < 2) return contentLength

    val rangeUrl = MediaStream.getSegmentUrl(url, contentLength - 2, contentLength - 1)
    if (httpController.getContentLength(rangeUrl) == null) return null

    return contentLength
}

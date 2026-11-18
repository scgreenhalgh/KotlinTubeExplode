package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.exceptions.RequestLimitExceededException
import java.io.IOException

/**
 * True when a failed request tells us about the network or YouTube's servers rather than about
 * the client that made it: transport errors (timeouts, DNS, connection resets), HTTP 5xx, and
 * rate limits. Every player request goes to www.youtube.com, so another client would most likely
 * hit the same failure, and none of these say anything about the video itself.
 *
 * An HTTP 4xx (say, a client version YouTube has retired) or an unreadable response is about the
 * one client, so callers move on to the next client for those.
 */
internal fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is RequestLimitExceededException -> true
    is HttpException -> statusCode >= 500
    is IOException -> true
    else -> false
}

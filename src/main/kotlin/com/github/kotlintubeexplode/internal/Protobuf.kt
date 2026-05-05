package com.github.kotlintubeexplode.internal

import java.util.Base64

/**
 * Minimal protobuf decoder for `map<string, string>` payloads.
 *
 * YouTube uses base64-encoded protobuf maps in fields like `xtags` on
 * stream formats. We don't need a general-purpose protobuf library — only
 * enough to read top-level LEN-encoded map entries with string keys and
 * string values.
 */
internal object Protobuf {

    private const val WIRE_TYPE_LEN = 2

    private class Reader(val data: ByteArray) {
        var pos = 0

        fun atEnd(): Boolean = pos >= data.size

        /** Reads a base-128 varint. Returns null on truncation or overflow. */
        fun readVarint(): ULong? {
            var value = 0UL
            var shift = 0
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                value = value or ((b.toULong() and 0x7FUL) shl shift)
                if (b and 0x80 == 0) return value
                shift += 7
                if (shift >= 64) return null
            }
            return null
        }

        /** Reads a length-prefixed UTF-8 string. Returns null on truncation. */
        fun readString(): String? {
            val length = readVarint() ?: return null
            if (length > Int.MAX_VALUE.toULong()) return null
            val len = length.toInt()
            if (pos + len > data.size) return null
            val s = String(data, pos, len, Charsets.UTF_8)
            pos += len
            return s
        }
    }

    private fun isLenField(tag: ULong): Boolean = (tag and 0x7UL).toInt() == WIRE_TYPE_LEN

    /**
     * Decodes a protobuf-encoded `map<string, string>` payload.
     * Each top-level LEN field is treated as a map entry submessage with
     * field 1 = key (string) and field 2 = value (string).
     * Returns null if the payload is malformed.
     */
    fun tryDeserializeMap(data: ByteArray): Map<String, String?>? {
        val result = mutableMapOf<String, String?>()
        val reader = Reader(data)

        while (!reader.atEnd()) {
            val outerTag = reader.readVarint() ?: return null
            if (!isLenField(outerTag)) return null

            val entryLen = reader.readVarint() ?: return null
            if (entryLen > Int.MAX_VALUE.toULong()) return null
            val entryEnd = reader.pos + entryLen.toInt()
            if (entryEnd > data.size) return null

            var key: String? = null
            var value: String? = null
            while (reader.pos < entryEnd) {
                val fieldTag = reader.readVarint() ?: break
                if (!isLenField(fieldTag)) break

                val fieldNum = (fieldTag shr 3).toInt()
                val str = reader.readString() ?: break

                when (fieldNum) {
                    1 -> key = str
                    2 -> value = str
                }
            }

            if (key != null) result[key] = value
            reader.pos = entryEnd
        }

        return result
    }

    /**
     * Decodes a base64-encoded protobuf map payload.
     * Returns null if the input isn't valid base64 or the payload is malformed.
     */
    fun tryDeserializeMap(base64: String): Map<String, String?>? {
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return tryDeserializeMap(bytes)
    }
}

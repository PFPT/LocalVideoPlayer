package com.pfpt.localvideoplayer.download

import java.io.IOException
import java.net.URI
import java.security.MessageDigest

internal data class HlsVodPlaylist(
    val url: String,
    val signature: String,
    val initSegmentUrl: String? = null,
    val segments: List<HlsSegment>,
)

internal enum class HlsSegmentFormat {
    TS,
    FMP4,
}

internal fun HlsVodPlaylist.segmentFormat(): HlsSegmentFormat = when {
    initSegmentUrl != null -> HlsSegmentFormat.FMP4
    segments.any { segment ->
        val path = segment.url.substringBefore('?').substringBefore('#')
        FMP4_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }
    } -> HlsSegmentFormat.FMP4
    else -> HlsSegmentFormat.TS
}

private val FMP4_EXTENSIONS = listOf(".m4s", ".mp4", ".m4v", ".cmfv", ".cmfa")

internal fun HlsSegmentFormat.segmentExtension(): String = when (this) {
    HlsSegmentFormat.TS -> "ts"
    HlsSegmentFormat.FMP4 -> "m4s"
}

internal data class HlsSegment(
    val url: String,
    val durationSec: Double,
    val encryption: HlsEncryption?,
)

internal data class HlsEncryption(
    val keyUrl: String,
    val iv: ByteArray,
)

internal enum class HlsVodFailure {
    INVALID,
    LIVE,
    UNSUPPORTED_MASTER,
    UNSUPPORTED_ENCRYPTION,
    UNSUPPORTED_FRAGMENTED,
    UNSUPPORTED_TS,
    UNSUPPORTED_SEGMENT,
}

internal class HlsVodException(val failure: HlsVodFailure) : IOException()

internal data class HlsVodResolveResult(
    val playlist: HlsVodPlaylist,
    val mediaText: String,
)

internal class HlsVodResolver(
    private val requestText: (String) -> String,
) {
    fun resolve(playlistUrl: String): HlsVodPlaylist = resolveWithMediaText(playlistUrl).playlist

    fun resolveWithMediaText(playlistUrl: String): HlsVodResolveResult {
        val initialText = requestText(playlistUrl)
        val selectedUrl = HlsVodPlaylistParser.selectVariant(playlistUrl, initialText)
        val mediaText = if (selectedUrl == null) initialText else requestText(selectedUrl)
        val mediaUrl = selectedUrl ?: playlistUrl
        return HlsVodResolveResult(
            playlist = HlsVodPlaylistParser.parseMediaPlaylist(mediaUrl, mediaText),
            mediaText = mediaText,
        )
    }
}

internal object HlsVodPlaylistParser {
    fun selectVariant(playlistUrl: String, text: String): String? {
        val lines = lines(text)
        requireHeader(lines)
        if (lines.any { it.startsWith("#EXT-X-I-FRAME-STREAM-INF") }) {
            throw HlsVodException(HlsVodFailure.UNSUPPORTED_MASTER)
        }
        if (lines.none { it.startsWith("#EXT-X-STREAM-INF:") }) return null
        if (lines.any { it.startsWith("#EXT-X-KEY") || it.startsWith("#EXT-X-SESSION-KEY") }) {
            throw HlsVodException(HlsVodFailure.UNSUPPORTED_ENCRYPTION)
        }

        val variants = mutableListOf<Pair<Long, String>>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) {
                index++
                continue
            }
            val bandwidth = attributes(line.substringAfter(':'))["BANDWIDTH"]?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: throw HlsVodException(HlsVodFailure.INVALID)
            val variantUri = lines.getOrNull(index + 1)
                ?.takeIf { !it.startsWith("#") }
                ?: throw HlsVodException(HlsVodFailure.INVALID)
            variants += bandwidth to resolve(playlistUrl, variantUri)
            index += 2
        }
        return variants.maxByOrNull { it.first }?.second
            ?: throw HlsVodException(HlsVodFailure.INVALID)
    }

    fun parseMediaPlaylist(playlistUrl: String, text: String): HlsVodPlaylist {
        val lines = lines(text)
        requireHeader(lines)
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF:") || it.startsWith("#EXT-X-I-FRAME-STREAM-INF") }) {
            throw HlsVodException(HlsVodFailure.UNSUPPORTED_MASTER)
        }
        if (lines.none { it == "#EXT-X-ENDLIST" }) throw HlsVodException(HlsVodFailure.LIVE)
        if (lines.count { it == "#EXT-X-ENDLIST" } != 1 || lines.last() != "#EXT-X-ENDLIST") {
            throw HlsVodException(HlsVodFailure.INVALID)
        }
        if (lines.any { it.startsWith("#EXT-X-BYTERANGE") }) {
            throw HlsVodException(HlsVodFailure.UNSUPPORTED_FRAGMENTED)
        }
        if (lines.any { it.startsWith("#EXT-X-SESSION-KEY") }) {
            throw HlsVodException(HlsVodFailure.UNSUPPORTED_ENCRYPTION)
        }

        var mediaSequence = 0L
        var sequenceDeclared = false
        var key: Key? = null
        var pendingDuration: Double? = null
        var initSegmentUrl: String? = null
        val segments = mutableListOf<HlsSegment>()
        lines.drop(1).forEach { line ->
            when {
                line == "#EXT-X-ENDLIST" -> Unit
                line.startsWith("#EXT-X-DISCONTINUITY") -> Unit
                line.startsWith("#EXTINF:") -> {
                    if (pendingDuration != null) throw HlsVodException(HlsVodFailure.INVALID)
                    pendingDuration = line.substringAfter(':').substringBefore(',').toDoubleOrNull()
                        ?: throw HlsVodException(HlsVodFailure.INVALID)
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    if (pendingDuration != null) throw HlsVodException(HlsVodFailure.INVALID)
                    key = parseKey(playlistUrl, line.substringAfter(':'))
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    if (pendingDuration != null) throw HlsVodException(HlsVodFailure.INVALID)
                    if (initSegmentUrl != null) throw HlsVodException(HlsVodFailure.INVALID)
                    initSegmentUrl = parseMapUri(playlistUrl, line.substringAfter(':'))
                }
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    if (sequenceDeclared || segments.isNotEmpty() || pendingDuration != null) {
                        throw HlsVodException(HlsVodFailure.INVALID)
                    }
                    mediaSequence = line.substringAfter(':').toLongOrNull()?.takeIf { it >= 0 }
                        ?: throw HlsVodException(HlsVodFailure.INVALID)
                    sequenceDeclared = true
                }
                line.startsWith("#") -> {
                    if (!isSafeMediaTag(line)) throw HlsVodException(HlsVodFailure.INVALID)
                }
                else -> {
                    val duration = pendingDuration
                    if (duration == null || !isSegmentUri(line)) {
                        throw HlsVodException(
                            if (line.contains(".ts", ignoreCase = true) || line.contains(".m4s", ignoreCase = true)) {
                                HlsVodFailure.UNSUPPORTED_TS
                            } else {
                                HlsVodFailure.UNSUPPORTED_SEGMENT
                            },
                        )
                    }
                    val sequence = mediaSequence + segments.size
                    if (sequence < 0) throw HlsVodException(HlsVodFailure.INVALID)
                    segments += HlsSegment(
                        url = resolve(playlistUrl, line),
                        durationSec = duration,
                        encryption = key?.let { HlsEncryption(it.url, it.iv ?: deriveIv(sequence)) },
                    )
                    pendingDuration = null
                }
            }
        }
        if (pendingDuration != null || segments.isEmpty()) throw HlsVodException(HlsVodFailure.INVALID)
        return HlsVodPlaylist(
            url = playlistUrl,
            signature = signature(playlistUrl, text),
            initSegmentUrl = initSegmentUrl,
            segments = segments,
        )
    }

    private fun parseMapUri(playlistUrl: String, value: String): String {
        val uri = attributes(value)["URI"] ?: throw HlsVodException(HlsVodFailure.INVALID)
        return resolve(playlistUrl, uri)
    }

    fun parseIv(value: String): ByteArray {
        val hex = value.removePrefix("0x").removePrefix("0X")
        if (hex.length != 32 || hex.any { it.digitToIntOrNull(16) == null }) {
            throw HlsVodException(HlsVodFailure.INVALID)
        }
        return ByteArray(16) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun parseKey(playlistUrl: String, value: String): Key? {
        val attributes = attributes(value)
        return when (attributes["METHOD"]) {
            "NONE" -> {
                if (attributes.keys != setOf("METHOD")) throw HlsVodException(HlsVodFailure.INVALID)
                null
            }
            "AES-128" -> {
                val keyUri = attributes["URI"] ?: throw HlsVodException(HlsVodFailure.INVALID)
                val iv = attributes["IV"]?.let(::parseIv)
                if (attributes.keys.any { it !in setOf("METHOD", "URI", "IV") }) {
                    throw HlsVodException(HlsVodFailure.UNSUPPORTED_ENCRYPTION)
                }
                Key(resolve(playlistUrl, keyUri), iv)
            }
            else -> throw HlsVodException(HlsVodFailure.UNSUPPORTED_ENCRYPTION)
        }
    }

    private fun isSafeMediaTag(line: String): Boolean = when {
        !line.startsWith("#EXT-X-") -> true
        line.startsWith("#EXT-X-VERSION:") -> line.substringAfter(':').toIntOrNull()?.let { it > 0 } == true
        line.startsWith("#EXT-X-TARGETDURATION:") -> line.substringAfter(':').toLongOrNull()?.let { it > 0 } == true
        else -> line == "#EXT-X-PLAYLIST-TYPE:VOD" || line == "#EXT-X-INDEPENDENT-SEGMENTS"
    }

    private fun attributes(value: String): Map<String, String> {
        val matches = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(value).toList()
        if (matches.isEmpty() || matches.joinToString(",") { it.value } != value) {
            throw HlsVodException(HlsVodFailure.INVALID)
        }
        return matches.associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
        }.also { if (it.size != matches.size) throw HlsVodException(HlsVodFailure.INVALID) }
    }

    private fun lines(text: String): List<String> = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()

    private fun requireHeader(lines: List<String>) {
        if (lines.firstOrNull() != "#EXTM3U") throw HlsVodException(HlsVodFailure.INVALID)
    }

    private fun resolve(baseUrl: String, reference: String): String = try {
        URI(baseUrl).resolve(reference).toString()
    } catch (_: IllegalArgumentException) {
        throw HlsVodException(HlsVodFailure.INVALID)
    }

    private fun signature(url: String, text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((url + "\n" + text).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isSegmentUri(line: String): Boolean =
        line.substringBefore('#').trim().isNotEmpty()

    private fun deriveIv(sequence: Long): ByteArray = ByteArray(16).also { iv ->
        for (index in 0 until 8) {
            iv[15 - index] = (sequence ushr (index * 8)).toByte()
        }
    }

    private data class Key(val url: String, val iv: ByteArray?)
}

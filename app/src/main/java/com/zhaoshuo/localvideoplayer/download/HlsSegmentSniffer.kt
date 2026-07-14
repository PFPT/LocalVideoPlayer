package com.zhaoshuo.localvideoplayer.download

import java.io.File
import java.io.IOException

internal object HlsSegmentSniffer {
    fun detect(segmentFiles: List<File>, playlistFormat: HlsSegmentFormat): HlsSegmentFormat {
        for (file in segmentFiles) {
            if (!file.exists() || file.length() <= 0) continue
            detectBytes(readHeader(file))?.let { return it }
        }
        return playlistFormat
    }

    fun isRecognizedMedia(segmentFiles: List<File>): Boolean = findInvalidSegment(segmentFiles) == null

    fun findInvalidSegment(segmentFiles: List<File>): Int? {
        if (segmentFiles.isEmpty()) return 0
        segmentFiles.forEachIndexed { index, file ->
            if (!file.exists() || file.length() <= 0L) return index
            if (detectBytes(readHeader(file)) == null) return index
        }
        return null
    }

    fun detectBytes(header: ByteArray): HlsSegmentFormat? {
        if (header.isEmpty()) return null
        if (header[0] == 0x47.toByte()) return HlsSegmentFormat.TS
        if (header.size >= 8) {
            val boxType = String(header, 4, 4, Charsets.US_ASCII)
            if (boxType == "ftyp" || boxType == "moof" || boxType == "mdat" || boxType == "styp") {
                return HlsSegmentFormat.FMP4
            }
        }
        return null
    }

    private fun readHeader(file: File): ByteArray =
        file.inputStream().use { input ->
            val buffer = ByteArray(16)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
}

package com.pfpt.localvideoplayer.download

import java.io.File

internal object HlsSegmentSniffer {
    private const val TS_PACKET_SIZE = 188
    private const val DISGUISE_SCAN_BYTES = 4096
    private const val TS_SYNC = 0x47.toByte()

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

    fun tsSyncOffset(prefix: ByteArray): Int? {
        val limit = minOf(prefix.size, DISGUISE_SCAN_BYTES) - TS_PACKET_SIZE * 2
        if (limit <= 1) return null
        for (offset in 1 until limit) {
            if (prefix[offset] == TS_SYNC &&
                prefix[offset + TS_PACKET_SIZE] == TS_SYNC &&
                offset + TS_PACKET_SIZE * 2 < prefix.size &&
                prefix[offset + TS_PACKET_SIZE * 2] == TS_SYNC
            ) {
                return offset
            }
        }
        return null
    }

    fun stripDisguise(file: File) {
        if (!file.exists() || file.length() <= TS_PACKET_SIZE * 3) return
        val scanLength = minOf(file.length(), DISGUISE_SCAN_BYTES.toLong()).toInt()
        val prefix = ByteArray(scanLength)
        val read = file.inputStream().use { it.read(prefix) }
        if (read < TS_PACKET_SIZE * 3) return
        val scanned = if (read == prefix.size) prefix else prefix.copyOf(read)
        if (detectBytes(scanned) != null) return
        val offset = tsSyncOffset(scanned) ?: return
        val temp = File(file.parentFile, "${file.name}.media")
        file.inputStream().use { input ->
            var remaining = offset.toLong()
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) {
                    temp.delete()
                    return
                }
                remaining -= skipped
            }
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        if (temp.length() <= 0L) {
            temp.delete()
            return
        }
        if (!file.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    fun detectBytes(header: ByteArray): HlsSegmentFormat? {
        if (header.isEmpty()) return null
        if (header[0] == TS_SYNC) return HlsSegmentFormat.TS
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

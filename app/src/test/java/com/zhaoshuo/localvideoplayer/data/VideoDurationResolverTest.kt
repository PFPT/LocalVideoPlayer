package com.zhaoshuo.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDurationResolverTest {
    @Test
    fun primaryStorageFileJoinsDirectoryAndTitle() {
        val legacyPath = primaryStorageFile("Download/a.mp4", "a.mp4")
        assertEquals("/storage/emulated/0/Download/a.mp4", legacyPath?.absolutePath)

        val mediaStorePath = primaryStorageFile("Download/", "a.mp4")
        assertEquals("/storage/emulated/0/Download/a.mp4", mediaStorePath?.absolutePath)

        val nested = primaryStorageFile("Movies/Show/", "ep01.mkv")
        assertEquals("/storage/emulated/0/Movies/Show/ep01.mkv", nested?.absolutePath)
    }

    @Test
    fun pngSignatureWithTsAfterIendIsDisguisedMpegTs() {
        val header = buildPngHeaderWithTrailer(
            trailer = byteArrayOf(0x00, TS_SYNC, 0x40, 0x11),
        )
        assertTrue(isPngHeader(header))
        assertTrue(isDisguisedMpegTsHeader(header))
        assertTrue(shouldSkipFastProbe(header))
    }

    @Test
    fun normalMp4HeaderDoesNotSkipFastProbe() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
        )
        assertFalse(shouldSkipFastProbe(header))
    }

    @Test
    fun pngWithFFmpegMarkerIsDisguisedMpegTs() {
        val marker = "FFmpeg.Service01".toByteArray(Charsets.ISO_8859_1)
        val header = buildPngHeaderWithTrailer(trailer = marker)
        assertTrue(isDisguisedMpegTsHeader(header))
    }

    @Test
    fun plainPngWithoutTsIsNotDisguised() {
        val header = buildPngHeaderWithTrailer(trailer = byteArrayOf(0x00, 0x01, 0x02))
        assertTrue(isPngHeader(header))
        assertFalse(isDisguisedMpegTsHeader(header))
    }

    @Test
    fun bmpWithService01IsDisguisedMpegTs() {
        val header = ByteArray(64)
        header[0] = 'B'.code.toByte()
        header[1] = 'M'.code.toByte()
        val marker = "Service01".toByteArray(Charsets.ISO_8859_1)
        marker.copyInto(header, destinationOffset = 20)
        assertTrue(isDisguisedMpegTsHeader(header))
    }

    @Test
    fun oldWrongIhdrOffsetAloneIsNotPng() {
        val header = ByteArray(16)
        header[4] = 'I'.code.toByte()
        header[5] = 'H'.code.toByte()
        header[6] = 'D'.code.toByte()
        header[7] = 'R'.code.toByte()
        assertFalse(isPngHeader(header))
        assertFalse(isDisguisedMpegTsHeader(header))
    }

    private fun buildPngHeaderWithTrailer(trailer: ByteArray): ByteArray {
        val pngSig = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        val ihdr = byteArrayOf(
            0, 0, 0, 13,
            'I'.code.toByte(),
            'H'.code.toByte(),
            'D'.code.toByte(),
            'R'.code.toByte(),
        )
        val iend = byteArrayOf(
            'I'.code.toByte(),
            'E'.code.toByte(),
            'N'.code.toByte(),
            'D'.code.toByte(),
        )
        return pngSig + ihdr + ByteArray(8) + iend + trailer
    }

    companion object {
        private const val TS_SYNC: Byte = 0x47
    }
}

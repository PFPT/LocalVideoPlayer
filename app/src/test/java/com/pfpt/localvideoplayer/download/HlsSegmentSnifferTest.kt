package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HlsSegmentSnifferTest {
    @Test
    fun detectsTsBySyncByte() {
        assertTrue(HlsSegmentSniffer.isRecognizedMedia(listOf(bytesFile(0x47, 0x40, 0x00))))
    }

    @Test
    fun detectsFmp4ByFtypBox() {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x18) + "ftyp".toByteArray() + "mp42".toByteArray()
        assertTrue(HlsSegmentSniffer.isRecognizedMedia(listOf(File.createTempFile("seg", ".m4s").apply { writeBytes(header) })))
    }

    @Test
    fun stripsPngDisguiseBeforeTsSync() {
        val prefix = ByteArray(126)
        prefix[0] = 0x89.toByte()
        prefix[1] = 0x50
        prefix[2] = 0x4E
        prefix[3] = 0x47
        val packet = ByteArray(188)
        packet[0] = 0x47
        val disguised = prefix + packet + packet + packet
        val offset = HlsSegmentSniffer.tsSyncOffset(disguised)
        assertEquals(126, offset)
        assertEquals(
            HlsSegmentFormat.TS,
            HlsSegmentSniffer.detectBytes(disguised.copyOfRange(offset!!, offset + 16)),
        )
    }

    @Test
    fun rejectsCorruptSegments() {
        assertFalse(HlsSegmentSniffer.isRecognizedMedia(listOf(bytesFile(0x10, 0x20, 0x30))))
    }

    @Test
    fun findInvalidSegmentDetectsEmptyFile() {
        val dir = createTempDir()
        try {
            val valid = File(dir, "seg00000.ts").apply { writeBytes(byteArrayOf(0x47, 0x40, 0x00)) }
            val empty = File(dir, "seg00001.ts").apply { createNewFile() }
            assertEquals(1, HlsSegmentSniffer.findInvalidSegment(listOf(valid, empty)))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun bytesFile(vararg bytes: Int): File =
        File.createTempFile("seg", ".ts").apply { writeBytes(bytes.map { it.toByte() }.toByteArray()) }
}

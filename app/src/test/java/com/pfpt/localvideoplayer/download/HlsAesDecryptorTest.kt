package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsAesDecryptorTest {
    @Test
    fun streamDecrypt_matchesDoFinal() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(16) { (it + 7).toByte() }
        val plain = ByteArray(64) { (it * 3).toByte() }
        val encrypted = Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(plain)
        }

        assertArrayEquals(plain, HlsAesDecryptor.decrypt(encrypted, key, iv))

        val streamed = ByteArrayOutputStream()
        HlsAesDecryptor.copyDecrypting(ByteArrayInputStream(encrypted), streamed, key, iv)
        assertArrayEquals(plain, streamed.toByteArray())
    }

    @Test
    fun decryptingOutputStream_matchesDoFinal() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { (15 - it).toByte() }
        val plain = ByteArray(48) { (100 - it).toByte() }
        val encrypted = Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(plain)
        }

        val out = ByteArrayOutputStream()
        HlsAesDecryptor.decryptingOutputStream(out, key, iv).use { decrypted ->
            var offset = 0
            while (offset < encrypted.size) {
                val chunk = minOf(17, encrypted.size - offset)
                decrypted.write(encrypted, offset, chunk)
                offset += chunk
            }
        }
        assertArrayEquals(plain, out.toByteArray())
        assertEquals(plain.size, out.size())
    }
}

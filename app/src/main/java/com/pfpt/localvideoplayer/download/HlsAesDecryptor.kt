package com.pfpt.localvideoplayer.download

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object HlsAesDecryptor {
    private const val BLOCK_BYTES = 16
    private const val TRANSFORMATION = "AES/CBC/NoPadding"

    fun decrypt(encryptedBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        if (encryptedBytes.isEmpty() || encryptedBytes.size % BLOCK_BYTES != 0) {
            throw IOException("invalid encrypted segment size")
        }
        return newCipher(key, iv).doFinal(encryptedBytes)
    }

    fun decryptingOutputStream(destination: OutputStream, key: ByteArray, iv: ByteArray): OutputStream =
        CipherOutputStream(destination, newCipher(key, iv))

    fun copyDecrypting(source: InputStream, destination: OutputStream, key: ByteArray, iv: ByteArray, bufferSize: Int = 64 * 1024) {
        decryptingOutputStream(destination, key, iv).use { decrypted ->
            val buffer = ByteArray(bufferSize)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                decrypted.write(buffer, 0, count)
            }
            decrypted.flush()
        }
    }

    private fun newCipher(key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
}

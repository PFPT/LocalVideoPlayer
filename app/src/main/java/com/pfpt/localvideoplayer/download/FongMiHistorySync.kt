package com.pfpt.localvideoplayer.download

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal object FongMiHistorySync {
    private const val TIMEOUT_MS = 10_000
    private const val READY_TIMEOUT_MS = 2_000L
    private const val FAIL_FAST_MS = 500L

    fun attempt(baseUrl: String, deviceBody: String): String? {
        val targetsPayload = AtomicReference<String?>(null)
        val readyLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(1)
        val server = ServerSocket(0)
        try {
            val listenPort = server.localPort
            val syncDevice = JSONObject(deviceBody).apply {
                put("ip", "http://127.0.0.1:$listenPort")
            }
            thread(name = "fongmi-history-sync", isDaemon = true) {
                try {
                    readyLatch.countDown()
                    server.accept().use { socket ->
                        socket.soTimeout = TIMEOUT_MS
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        if (!requestLine.startsWith("POST", ignoreCase = true)) return@thread
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                headers[line.substring(0, colon).trim().lowercase()] =
                                    line.substring(colon + 1).trim()
                            }
                        }
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = if (contentLength > 0) {
                            val buffer = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val count = reader.read(buffer, read, contentLength - read)
                                if (count < 0) break
                                read += count
                            }
                            String(buffer, 0, read.coerceAtMost(contentLength))
                        } else {
                            reader.readText()
                        }
                        targetsPayload.set(parseTargetsPayload(body))
                        OutputStreamWriter(socket.getOutputStream()).use { writer ->
                            writer.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK")
                            writer.flush()
                        }
                    }
                } catch (_: IOException) {
                } finally {
                    doneLatch.countDown()
                }
            }
            readyLatch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val posted = postHistorySync(baseUrl, syncDevice.toString())
            if (!posted) {
                runCatching { server.close() }
                doneLatch.await(FAIL_FAST_MS, TimeUnit.MILLISECONDS)
                return targetsPayload.get()
            }
            doneLatch.await(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        } finally {
            runCatching { server.close() }
        }
        return targetsPayload.get()
    }

    private fun postHistorySync(baseUrl: String, deviceJson: String): Boolean {
        val configJson = """{"type":0}"""
        val connection = (
            URL(
                "$baseUrl/action?do=sync&type=history&mode=2&force=false" +
                    "&config=${encodeQuery(configJson)}" +
                    "&device=${encodeQuery(deviceJson)}",
            ).openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
        }
        return try {
            connection.outputStream.use { it.write(ByteArray(0)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTargetsPayload(body: String): String? {
        if (body.isBlank()) return null
        extractFormValue(body, "targets")?.let { return it }
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return trimmed
        if (trimmed.startsWith("{")) {
            runCatching {
                JSONObject(trimmed).optString("targets").trim().takeIf { it.isNotBlank() }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun extractFormValue(body: String, key: String): String? {
        if (body.isBlank()) return null
        return body.split('&').firstNotNullOfOrNull { part ->
            val partKey = part.substringBefore('=')
            if (!partKey.equals(key, ignoreCase = true)) return@firstNotNullOfOrNull null
            DownloadUrlCodec.decodeOnce(part.substringAfter('='))
        }
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

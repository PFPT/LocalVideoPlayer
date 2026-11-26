package com.pfpt.localvideoplayer.cast

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LocalCastServer(
    private val appContext: Context,
) {
    private val running = AtomicBoolean(false)
    private val mediaRef = AtomicReference<MediaSource?>(null)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val activeClients = AtomicInteger(0)
    private var clients: ThreadPoolExecutor = newClientPool()

    @Volatile
    var baseUrl: String? = null
        private set

    @Volatile
    private var streamToken: String = ""

    private val failure = AtomicReference<String?>(null)

    val lastFailure: String?
        get() = failure.get()

    val streamUrl: String?
        get() {
            val root = baseUrl ?: return null
            val token = streamToken
            if (token.isBlank()) return null
            return root + CastRequest.mediaPath(token)
        }

    @Synchronized
    fun start(uri: Uri, mimeType: String? = null): String {
        stop()
        failure.set(null)
        clients = newClientPool()
        if (!canOpen(uri)) throw IllegalStateException("Unable to open media")
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: guessMime(uri)
        val length = queryLength(uri)
        mediaRef.set(MediaSource(uri, resolvedMime, length))
        streamToken = UUID.randomUUID().toString().replace("-", "")

        val ip = localIpv4() ?: throw IllegalStateException("No LAN IPv4 address")
        val port = bindPort(InetAddress.getByName(ip))
        baseUrl = "http://$ip:$port"
        running.set(true)
        acceptThread = Thread({
            val socket = serverSocket ?: return@Thread
            while (running.get()) {
                try {
                    val client = socket.accept()
                    if (activeClients.get() >= MAX_CONCURRENT_CLIENTS) {
                        try {
                            client.close()
                        } catch (_: Exception) {
                        }
                        continue
                    }
                    clients.execute { handleClient(client) }
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }, "LocalCastServer").also {
            it.isDaemon = true
            it.start()
        }
        return streamUrl ?: throw IllegalStateException("Cast stream URL was not created")
    }

    @Synchronized
    fun stop() {
        running.set(false)
        baseUrl = null
        streamToken = ""
        mediaRef.set(null)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        clients.shutdownNow()
        try {
            clients.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        activeClients.set(0)
    }

    private fun newClientPool(): ThreadPoolExecutor {
        return Executors.newFixedThreadPool(MAX_CONCURRENT_CLIENTS) as ThreadPoolExecutor
    }

    private fun bindPort(address: InetAddress): Int {
        var lastError: Exception? = null
        for (port in PORT_START..PORT_END) {
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(address, port))
                serverSocket = socket
                return port
            } catch (e: Exception) {
                runCatching { socket.close() }
                lastError = e
            }
        }
        throw IllegalStateException("Unable to bind cast server port", lastError)
    }

    private fun handleClient(socket: Socket) {
        activeClients.incrementAndGet()
        socket.soTimeout = 30_000
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val requestLine = readLine(input) ?: return
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase(Locale.US)] =
                        line.substring(idx + 1).trim()
                }
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeSimple(output, 400, "Bad Request")
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val path = parts[1].substringBefore('?')
            if (!CastRequest.accepts(path, streamToken)) {
                writeSimple(output, 404, "Not Found")
                return
            }
            if (method != "GET" && method != "HEAD") {
                writeSimple(output, 405, "Method Not Allowed")
                return
            }
            val media = mediaRef.get()
            if (media == null) {
                writeSimple(output, 503, "Service Unavailable")
                return
            }
            serveMedia(method, media, headers["range"], output)
        } catch (error: Exception) {
            if (!isClientDisconnect(error)) {
                failure.set(error.message ?: error.javaClass.simpleName)
            }
        } finally {
            activeClients.decrementAndGet()
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun serveMedia(
        method: String,
        media: MediaSource,
        rangeHeader: String?,
        output: BufferedOutputStream,
    ) {
        val total = media.length
        val range = CastRequest.parseRange(rangeHeader, total)
        val start = range?.first ?: 0L
        val end = range?.second ?: if (total > 0) (total - 1) else -1L
        val contentLength = when {
            total > 0 && end >= start -> end - start + 1
            else -> -1L
        }
        val status = if (range != null) 206 else 200
        val statusText = if (range != null) "Partial Content" else "OK"

        val header = StringBuilder()
        header.append("HTTP/1.1 $status $statusText\r\n")
        header.append("Content-Type: ${media.mimeType}\r\n")
        header.append("Accept-Ranges: bytes\r\n")
        header.append("Connection: close\r\n")
        if (total > 0) {
            header.append("Content-Length: $contentLength\r\n")
            if (range != null) {
                header.append("Content-Range: bytes $start-$end/$total\r\n")
            }
        }
        header.append("\r\n")
        output.write(header.toString().toByteArray(Charsets.US_ASCII))
        output.flush()

        if (method == "HEAD") return

        openSeekable(media.uri)?.use { seekable ->
            if (start > 0) {
                seekable.channel.position(start)
            }
            val channelInput = Channels.newInputStream(seekable.channel)
            copyStream(channelInput, output, contentLength)
            return
        }

        openStream(media.uri).use { input ->
            if (start > 0) {
                skipFully(input, start)
            }
            copyStream(input, output, contentLength)
        }
    }

    private fun copyStream(input: InputStream, output: BufferedOutputStream, contentLength: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER)
        var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
        while (remaining > 0 && running.get()) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private data class SeekableStream(
        val channel: FileChannel,
        private val pfd: ParcelFileDescriptor,
        private val fis: FileInputStream,
    ) : AutoCloseable {
        override fun close() {
            try {
                fis.close()
            } catch (_: Exception) {
            }
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun openSeekable(uri: Uri): SeekableStream? {
        return try {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val fis = FileInputStream(pfd.fileDescriptor)
            val channel = fis.channel
            if (!channel.isOpen) {
                fis.close()
                pfd.close()
                return null
            }
            SeekableStream(channel, pfd, fis)
        } catch (_: Exception) {
            null
        }
    }

    private fun openStream(uri: Uri): InputStream {
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            return object : FileInputStream(pfd.fileDescriptor) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        try {
                            pfd.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        return appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open $uri")
    }

    private fun queryLength(uri: Uri): Long {
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val len = afd.length
            if (len >= 0) return len
        }
        try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val size = fis.channel.size()
                    if (size > 0) return size
                }
            }
        } catch (_: Exception) {
        }
        return -1L
    }

    private fun guessMime(uri: Uri): String {
        val type = appContext.contentResolver.getType(uri)
        if (!type.isNullOrBlank()) return type
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            ?.lowercase(Locale.US)
            .orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/mp4"
    }

    private fun canOpen(uri: Uri): Boolean {
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true }
                ?: (appContext.contentResolver.openInputStream(uri)?.use { true } ?: false)
        } catch (error: Exception) {
            failure.set(error.message ?: error.javaClass.simpleName)
            false
        }
    }

    private fun isClientDisconnect(error: Exception): Boolean {
        if (error is SocketException) return true
        val message = error.message?.lowercase(Locale.US).orEmpty()
        return message.contains("broken pipe") ||
            message.contains("connection reset") ||
            message.contains("socket closed")
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (input.read() < 0) break
            remaining -= 1
        }
    }

    private fun writeSimple(output: BufferedOutputStream, code: Int, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        val header =
            "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (builder.isEmpty()) null else builder.toString()
            }
            val ch = value.toChar()
            if (ch == '\n') break
            if (ch != '\r') builder.append(ch)
        }
        return builder.toString()
    }

    private fun localIpv4(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private data class MediaSource(
        val uri: Uri,
        val mimeType: String,
        val length: Long,
    )

    companion object {
        private const val PORT_START = 8080
        private const val PORT_END = 8090
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val MAX_CONCURRENT_CLIENTS = 4
    }
}

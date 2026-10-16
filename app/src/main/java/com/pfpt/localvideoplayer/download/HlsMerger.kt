package com.pfpt.localvideoplayer.download

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException

object HlsMerger {
    private const val LOCAL_PLAYLIST_NAME = "index.m3u8"
    private const val CONCAT_LIST_NAME = "segments.txt"
    private const val COMBINED_TS_NAME = "combined.ts"
    private const val COMBINED_FMP4_NAME = "combined.mp4"
    private const val ERROR_SUMMARY_LIMIT = 240
    private const val IO_BUFFER_SIZE = 256 * 1024

    internal data class MergeInput(
        val segmentFiles: List<File>,
        val durationsSec: List<Double>,
        val format: HlsSegmentFormat,
        val initFile: File? = null,
    )

    internal fun mergeToMp4(input: MergeInput, outputFile: File) {
        if (input.segmentFiles.isEmpty()) throw IOException("No segments to merge")
        if (input.durationsSec.size != input.segmentFiles.size) {
            throw IOException("Segment count does not match duration list")
        }
        val workDir = input.segmentFiles.first().parentFile ?: throw IOException("Missing segment directory")
        val format = HlsSegmentSniffer.detect(input.segmentFiles, input.format)
        HlsSegmentSniffer.findInvalidSegment(input.segmentFiles)?.let { index ->
            throw IOException("SEGMENTS_CORRUPT:$index")
        }
        val playlistFile = File(workDir, LOCAL_PLAYLIST_NAME)
        val concatListFile = File(workDir, CONCAT_LIST_NAME)
        val combinedTsFile = File(workDir, COMBINED_TS_NAME)
        val combinedFmp4File = File(workDir, COMBINED_FMP4_NAME)
        try {
            writeLocalPlaylist(playlistFile, input, format)
            writeConcatList(concatListFile, input)
            val errors = mutableListOf<String>()
            for (command in buildStrategies(playlistFile, concatListFile, combinedTsFile, combinedFmp4File, input, outputFile, format)) {
                outputFile.delete()
                val error = when (command) {
                    is MergeCommand.Ffmpeg -> runFfmpeg(command.command)
                    is MergeCommand.BinaryTs -> runBinaryTsMerge(input, combinedTsFile, outputFile, command.applyTsBsf)
                    is MergeCommand.BinaryFmp4 -> runBinaryFmp4Merge(input, combinedFmp4File, outputFile)
                }
                if (error == null && outputFile.exists() && outputFile.length() > 0L) return
                error?.let { errors += it }
            }
            throw IOException(errors.distinct().take(2).joinToString(" · ").ifBlank { "ffmpeg merge failed" })
        } finally {
            playlistFile.delete()
            concatListFile.delete()
            combinedTsFile.delete()
            combinedFmp4File.delete()
        }
    }

    internal fun writeLocalPlaylist(
        playlistFile: File,
        input: MergeInput,
        format: HlsSegmentFormat,
    ) {
        val targetDuration = input.durationsSec.maxOrNull()?.let { kotlin.math.ceil(it).toInt() }?.coerceAtLeast(1) ?: 1
        val lines = buildList {
            add("#EXTM3U")
            when (format) {
                HlsSegmentFormat.FMP4 -> {
                    add("#EXT-X-VERSION:6")
                    add("#EXT-X-INDEPENDENT-SEGMENTS")
                    val initName = input.initFile?.name ?: throw IOException("Missing init segment for fMP4 merge")
                    add("""#EXT-X-MAP:URI="$initName"""")
                }
                HlsSegmentFormat.TS -> {
                    add("#EXT-X-VERSION:3")
                    add("#EXT-X-TARGETDURATION:$targetDuration")
                    add("#EXT-X-MEDIA-SEQUENCE:0")
                    add("#EXT-X-PLAYLIST-TYPE:VOD")
                }
            }
            input.segmentFiles.zip(input.durationsSec).forEach { (segment, duration) ->
                add("#EXTINF:${formatDuration(duration)},")
                add(segment.name)
            }
            add("#EXT-X-ENDLIST")
        }
        playlistFile.writeText(lines.joinToString("\n"))
    }

    internal fun writeConcatList(concatListFile: File, input: MergeInput) {
        val mergeFiles = buildList {
            input.initFile?.let { add(it) }
            addAll(input.segmentFiles)
        }
        concatListFile.writeText(
            mergeFiles.joinToString("\n") { file ->
                "file '${escapeConcatPath(file.absolutePath)}'"
            },
        )
    }

    private sealed interface MergeCommand {
        data class Ffmpeg(val command: String) : MergeCommand
        data class BinaryTs(val applyTsBsf: Boolean) : MergeCommand
        data object BinaryFmp4 : MergeCommand
    }

    private fun buildStrategies(
        playlistFile: File,
        concatListFile: File,
        combinedTsFile: File,
        combinedFmp4File: File,
        input: MergeInput,
        outputFile: File,
        format: HlsSegmentFormat,
    ): List<MergeCommand> = when (format) {
        HlsSegmentFormat.TS -> listOf(
            MergeCommand.BinaryTs(applyTsBsf = false),
            MergeCommand.BinaryTs(applyTsBsf = true),
            MergeCommand.Ffmpeg(buildConcatCommand(concatListFile, outputFile, copyMode = true, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildConcatCommand(concatListFile, outputFile, copyMode = true, applyTsBsf = true)),
            MergeCommand.Ffmpeg(buildM3u8Command(playlistFile, outputFile, copyMode = true, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildM3u8Command(playlistFile, outputFile, copyMode = true, applyTsBsf = true)),
            MergeCommand.Ffmpeg(buildSingleInputCommand(combinedTsFile, outputFile, copyMode = false, applyTsBsf = false)),
        )
        HlsSegmentFormat.FMP4 -> listOf(
            MergeCommand.BinaryFmp4,
            MergeCommand.Ffmpeg(buildM3u8Command(playlistFile, outputFile, copyMode = true, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildConcatCommand(concatListFile, outputFile, copyMode = true, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildSingleInputCommand(combinedFmp4File, outputFile, copyMode = true, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildM3u8Command(playlistFile, outputFile, copyMode = false, applyTsBsf = false)),
            MergeCommand.Ffmpeg(buildConcatCommand(concatListFile, outputFile, copyMode = false, applyTsBsf = false)),
        )
    }

    private fun runBinaryTsMerge(
        input: MergeInput,
        combinedTsFile: File,
        outputFile: File,
        applyTsBsf: Boolean,
    ): String? {
        appendFiles(input.segmentFiles, combinedTsFile)
        return runFfmpeg(buildSingleInputCommand(combinedTsFile, outputFile, copyMode = true, applyTsBsf = applyTsBsf))
    }

    private fun runBinaryFmp4Merge(
        input: MergeInput,
        combinedFmp4File: File,
        outputFile: File,
    ): String? {
        val sources = buildList {
            input.initFile?.let { add(it) }
            addAll(input.segmentFiles)
        }
        appendFiles(sources, combinedFmp4File)
        return runFfmpeg(buildSingleInputCommand(combinedFmp4File, outputFile, copyMode = true, applyTsBsf = false))
    }

    private fun appendFiles(sources: List<File>, destination: File) {
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(IO_BUFFER_SIZE)
            sources.forEach { sourceFile ->
                sourceFile.inputStream().buffered().use { source ->
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun buildConcatCommand(
        concatListFile: File,
        outputFile: File,
        copyMode: Boolean,
        applyTsBsf: Boolean,
    ): String = buildSingleInputCommand(concatListFile, outputFile, copyMode, applyTsBsf, inputFormat = "concat")

    private fun buildM3u8Command(
        playlistFile: File,
        outputFile: File,
        copyMode: Boolean,
        applyTsBsf: Boolean,
    ): String = buildSingleInputCommand(playlistFile, outputFile, copyMode, applyTsBsf)

    private fun buildSingleInputCommand(
        inputFile: File,
        outputFile: File,
        copyMode: Boolean,
        applyTsBsf: Boolean,
        inputFormat: String? = null,
    ): String {
        val args = mutableListOf(
            "-hide_banner",
            "-loglevel", "error",
            "-y",
        )
        if (inputFormat != null) {
            args += listOf("-f", inputFormat, "-safe", "0")
        } else if (inputFile.extension.equals("m3u8", ignoreCase = true)) {
            args += listOf("-allowed_extensions", "ALL")
        }
        args += listOf("-i", inputFile.absolutePath)
        if (copyMode) {
            args += listOf("-c", "copy", "-movflags", "+faststart")
            if (applyTsBsf) args += listOf("-bsf:a", "aac_adtstoasc")
        } else {
            args += listOf("-c:v", "copy", "-c:a", "aac", "-movflags", "+faststart")
        }
        args += outputFile.absolutePath
        return args.joinToString(" ")
    }

    private fun runFfmpeg(command: String): String? {
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) return null
        val details = session.failStackTrace?.trim().orEmpty()
        val message = session.output?.trim().orEmpty()
        val error = listOfNotNull(
            message.takeIf { it.isNotBlank() },
            details.takeIf { it.isNotBlank() },
        ).joinToString("\n").ifBlank { "ffmpeg merge failed" }
        return summarizeError(error)
    }

    private fun summarizeError(error: String): String {
        val lines = error.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isFfmpegBannerLine(it) }
            .toList()
        if (lines.isEmpty()) return "ffmpeg merge failed"
        val prioritized = lines.filter { line ->
            line.contains("error", ignoreCase = true) ||
                line.contains("invalid", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true) ||
                line.contains("no such file", ignoreCase = true)
        }
        val chosen = (prioritized.ifEmpty { lines }).distinct().take(2).joinToString(" · ")
        if (chosen.length <= ERROR_SUMMARY_LIMIT) return chosen
        return chosen.take(ERROR_SUMMARY_LIMIT) + "…"
    }

    private fun isFfmpegBannerLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("ffmpeg version") ||
            lower.contains("copyright (c)") ||
            lower.startsWith("built with") ||
            lower.startsWith("configuration:") ||
            lower.startsWith("libavutil") ||
            lower.startsWith("libavcodec") ||
            lower.startsWith("libavformat") ||
            lower.startsWith("libavdevice") ||
            lower.startsWith("libavfilter") ||
            lower.startsWith("libswscale") ||
            lower.startsWith("libswresample")
    }

    private fun escapeConcatPath(path: String): String = path.replace("'", "'\\''")

    private fun formatDuration(durationSec: Double): String {
        if (durationSec <= 0) return "0.001"
        return "%.3f".format(durationSec).trimEnd('0').trimEnd('.').ifEmpty { "0.001" }
    }
}

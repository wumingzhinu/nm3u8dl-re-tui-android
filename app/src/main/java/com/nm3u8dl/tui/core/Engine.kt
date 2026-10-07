package com.nm3u8dl.tui.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class Track(
    val index: Int,
    val groupId: String,
    val kind: String,
    val language: String,
    val bandwidth: Long,
    val codecs: String,
    val resolution: String,
    val extension: String,
    val segments: Int,
    val encrypted: Boolean,
    val roles: List<String>,
    val selected: Boolean,
)

data class ProbeResult(
    val tracks: List<Track>,
    val live: Boolean,
    val durationSec: Double,
) {
    val videos: List<Track> get() = tracks.filter { it.kind == "video" }
    val audios: List<Track> get() = tracks.filter { it.kind == "audio" }
    val subs: List<Track> get() = tracks.filter { it.kind == "subtitle" }
}

data class Progress(
    val done: Int,
    val total: Int,
    val percent: Float,
    val bytes: String,
    val speed: String,
    val eta: String,
)

class Engine(private val context: Context) {

    private val assets = NativeAssets(context)
    private var paths: NativeAssets.Paths? = null

    suspend fun prepare(): NativeAssets.Paths = withContext(Dispatchers.IO) {
        paths ?: assets.ensureInstalled().also { paths = it }
    }

    private fun buildProcess(vararg args: String): ProcessBuilder {
        val p = prepareBlocking()
        val pb = ProcessBuilder(listOf(p.engine.absolutePath) + args)
        pb.environment()["LD_LIBRARY_PATH"] = p.libDir.absolutePath
        pb.environment()["DOTNET_SYSTEM_GLOBALIZATION_INVARIANT"] = "1"
        pb.environment()["HOME"] = context.filesDir.absolutePath
        pb.directory(File(context.filesDir, "work").apply { mkdirs() })
        return pb
    }

    private fun prepareBlocking(): NativeAssets.Paths {
        var p = paths
        if (p == null || !p.engine.exists()) {
            p = assets.ensureInstalled()
            paths = p
        }
        return p
    }

    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val p = prepare()
        val work = File(context.filesDir, "work").apply { mkdirs() }
        val tmp = File(work, "probe").apply { deleteRecursively(); mkdirs() }
        val args = listOf(
            url,
            "--skip-download",
            "--auto-select",
            "--write-meta-json", "true",
            "--tmp-dir", tmp.absolutePath,
            "--save-dir", work.absolutePath,
            "--ffmpeg-binary-path", p.ffmpeg.absolutePath,
            "--log-level", "WARN",
            "--no-log",
            "--no-config",
        )
        val proc = buildProcess(*args.toTypedArray()).start()
        val out = StringBuilder()
        proc.inputStream.bufferedReader().use { r -> r.readText().let { out.append(it) } }
        proc.errorStream.bufferedReader().use { it.readText().let { out.append(it) } }
        val code = proc.waitFor()

        val meta = tmp.listFiles()?.firstOrNull { it.name == "meta.json" }
            ?: throw IllegalStateException(
                "探测失败（退出码 $code）：无法解析该链接\n${out.toString().takeLast(500)}"
            )
        parseMeta(meta)
    }

    private fun parseMeta(file: File): ProbeResult {
        val text = file.readText().removePrefix("\uFEFF")
        val arr = JSONArray(text)
        val tracks = mutableListOf<Track>()
        var live = false
        var duration = 0.0

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pl = o.optJSONObject("Playlist")
            if (pl != null) {
                if (pl.optBoolean("IsLive", false)) live = true
                duration = maxOf(duration, pl.optDouble("TotalDuration", 0.0))
            }
            val rolesArr = o.optJSONArray("Roles")
            val roles = mutableListOf<String>()
            if (rolesArr != null) for (j in 0 until rolesArr.length()) roles.add(rolesArr.getString(j))

            val resolution = o.optString("Resolution", "")
            val language = o.optString("Language", "")
            val codecs = o.optString("Codecs", "")
            val rolesStr = roles.joinToString(",").lowercase()

            val kind = when {
                resolution.isNotEmpty() -> "video"
                "subtitle" in rolesStr || codecs.startsWith("wvtt") ||
                    codecs.startsWith("stpp") || codecs.startsWith("ttml") -> "subtitle"
                language.isNotEmpty() || codecs.startsWith("mp4a") ||
                    codecs.startsWith("ac-3") || codecs.startsWith("ec-3") -> "audio"
                else -> "other"
            }

            tracks.add(
                Track(
                    index = i,
                    groupId = o.optString("GroupId", ""),
                    kind = kind,
                    language = language,
                    bandwidth = o.optLong("Bandwidth", 0L),
                    codecs = codecs,
                    resolution = resolution,
                    extension = o.optString("Extension", ""),
                    segments = o.optInt("SegmentsCount", 0),
                    encrypted = o.optJSONObject("Playlist")
                        ?.optJSONArray("MediaParts")
                        ?.optJSONObject(0)
                        ?.optJSONArray("MediaSegments")
                        ?.optJSONObject(0)
                        ?.optBoolean("IsEncrypted", false) ?: false,
                    roles = roles,
                    selected = false,
                )
            )
        }
        return ProbeResult(tracks, live, duration)
    }

    suspend fun download(
        url: String,
        selection: List<Track>,
        outDir: File,
        key: String? = null,
        hlsKey: String? = null,
        hlsIv: String? = null,
        hlsMethod: String? = null,
        onLog: (String) -> Unit = {},
        onProgress: (Progress) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val p = prepare()
        val tmp = File(outDir, ".tmp").apply { mkdirs() }
        val args = mutableListOf(
            url,
            "--tmp-dir", tmp.absolutePath,
            "--save-dir", outDir.absolutePath,
            "--ffmpeg-binary-path", p.ffmpeg.absolutePath,
            "--log-level", "INFO",
            "--no-log",
            "--no-config",
            "--no-ansi-color",
        )
        val sharedKey = key?.trim().orEmpty()
        val hk = hlsKey?.trim().orEmpty()
        val hi = hlsIv?.trim().orEmpty()

        if (hk.isNotEmpty()) {
            args.add("--custom-hls-method")
            args.add(hlsMethod?.trim()?.ifEmpty { "AES_128" } ?: "AES_128")
            args.add("--custom-hls-key")
            args.add(hk)
            if (hi.isNotEmpty()) {
                args.add("--custom-hls-iv")
                args.add(hi)
            }
        }
        if (sharedKey.isNotEmpty()) {
            args.add("--decryption-engine")
            args.add("FFMPEG")
            sharedKey.split(",", " ", "\n").filter { it.isNotBlank() }.forEach {
                args.add("--key"); args.add(it.trim())
            }
        }
        val byKind = selection.groupBy { it.kind }
        byKind["video"]?.forEach {
            args.add("--select-video"); args.add("id=^${it.groupId}$")
        }
        byKind["audio"]?.forEach {
            args.add("--select-audio"); args.add("id=^${it.groupId}$")
        }
        byKind["subtitle"]?.forEach {
            args.add("--select-subtitle"); args.add("id=^${it.groupId}$")
        }
        if (selection.isEmpty()) args.add("--auto-select")

        val proc = buildProcess(*args.toTypedArray()).start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val errReader = BufferedReader(InputStreamReader(proc.errorStream))
        Thread {
            reader.forEachLine { line ->
                parseLine(line, onLog, onProgress)
            }
        }.start()
        Thread {
            errReader.forEachLine { line -> onLog("ERR: $line") }
        }.start()
        proc.waitFor()
    }

    private fun parseLine(raw: String, onLog: (String) -> Unit, onProgress: (Progress) -> Unit) {
        val line = raw.replace('\r', ' ').trimEnd()
        if (line.isBlank()) return
        val progRegex = Regex(
            """^(?:(?<label>[A-Za-z][A-Za-z ]*?)\s+)?[━═ ]{3,}\s*(\d+)/(\d+)\s+([\d.]+)%\s+([\w./]+)\s+(\S+)\s+(\S+)\s*$"""
        )
        val m = progRegex.find(line)
        if (m != null) {
            try {
                onProgress(
                    Progress(
                        done = m.groupValues[2].toInt(),
                        total = m.groupValues[3].toInt(),
                        percent = m.groupValues[4].toFloat(),
                        bytes = m.groupValues[5],
                        speed = m.groupValues[6],
                        eta = m.groupValues[7],
                    )
                )
                return
            } catch (_: Exception) {
            }
        }
        if (line.contains("INFO") || line.contains("WARN") || line.contains("ERROR")) {
            onLog(line.substringAfter(" : ", line))
        } else if (!line.startsWith("━") && !line.contains("━━")) {
            onLog(line)
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.2fGB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1fMB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0fKB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    @Suppress("unused")
    fun toHumanDuration(sec: Double): String {
        val s = sec.toLong()
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    @Suppress("unused")
    fun killActive() {
        runCatching { TimeUnit.MILLISECONDS.sleep(1) }
    }
}
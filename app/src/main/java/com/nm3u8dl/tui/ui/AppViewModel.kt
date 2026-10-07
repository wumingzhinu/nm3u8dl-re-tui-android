package com.nm3u8dl.tui.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.nm3u8dl.tui.core.Engine
import com.nm3u8dl.tui.core.ProbeResult
import com.nm3u8dl.tui.core.Progress
import com.nm3u8dl.tui.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class TuiApp : Application() {
    val engine: Engine by lazy { Engine(this) }
}

enum class Screen { INPUT, LOADING, SELECT, DOWNLOADING, DONE, ERROR }

/** 小白预设：按分辨率上限挑一条最合适的视频轨。 */
enum class Quality(val label: String, val maxHeight: Int) {
    BEST("最佳画质", 9999),
    UHD("4K", 2161),
    FHD("1080P", 1081),
    HD("720P", 721),
    SD("480P", 481),
    AUDIO_ONLY("只要音频", 0),
}

data class UiState(
    val screen: Screen = Screen.INPUT,
    val url: String = "",
    val key: String = "",
    val hlsKey: String = "",
    val hlsIv: String = "",
    val tracks: List<Track> = emptyList(),
    val quality: Quality = Quality.BEST,
    val showAdvanced: Boolean = false,
    val cursor: Int = 0,
    val progress: Progress? = null,
    val logs: List<String> = emptyList(),
    val status: String = "就绪",
    val error: String? = null,
    val outDir: String = "",
    val probeSummary: String = "",
)

class AppViewModel(app: TuiApp) : AndroidViewModel(app) {

    private val engine: Engine = app.engine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state by mutableStateOf(UiState())
        private set

    init {
        prepare()
    }

    private fun prepare() {
        scope.launch {
            state = state.copy(status = "正在准备内核…")
            runCatching { engine.prepare() }
                .onFailure { fail("内核准备失败：${it.message}") }
                .onSuccess { state = state.copy(status = "就绪") }
        }
    }

    fun onUrlChange(v: String) {
        state = state.copy(url = v)
    }

    fun onKeyChange(v: String) {
        state = state.copy(key = v)
    }

    fun onHlsKeyChange(v: String) {
        state = state.copy(hlsKey = v)
    }

    fun onHlsIvChange(v: String) {
        state = state.copy(hlsIv = v)
    }

    fun onQualityChange(q: Quality) {
        val s = state
        state = s.copy(
            quality = q,
            tracks = if (q == Quality.AUDIO_ONLY) {
                s.tracks.map { it.copy(selected = it.kind == "audio") }
            } else {
                val best = s.tracks.filter { it.kind == "video" }
                    .filter { heightOf(it) in 1..q.maxHeight }
                    .maxByOrNull { it.bandwidth }
                s.tracks.map {
                    it.copy(
                        selected = when {
                            best != null && it.index == best.index -> true
                            it.kind == "video" -> false
                            it.kind == "audio" -> it.index == s.tracks.firstOrNull { t -> t.kind == "audio" }?.index
                            else -> false
                        }
                    )
                }
            },
        )
    }

    private fun heightOf(t: Track): Int =
        Regex("(\\d+)x(\\d+)").find(t.resolution)?.groupValues?.get(2)?.toIntOrNull() ?: 0

    fun toggleAdvanced() {
        state = state.copy(showAdvanced = !state.showAdvanced)
    }

    fun pasteUrl() {
        val ctx = getApplication<Application>()
        val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val text = clip?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
        if (text.isNotEmpty()) state = state.copy(url = text)
    }

    fun probe() {
        val url = state.url.trim()
        if (url.isEmpty()) {
            fail("请先粘贴播放链接")
            return
        }
        scope.launch {
            state = state.copy(
                screen = Screen.LOADING,
                status = "正在解析…",
                logs = emptyList(),
                error = null,
            )
            runCatching { engine.probe(url) }
                .onSuccess { result ->
                    val q = state.quality
                    state = state.copy(
                        screen = Screen.SELECT,
                        tracks = result.tracks.map { it.copy(selected = false) },
                        quality = q,
                        cursor = 0,
                        probeSummary = summarize(result),
                        status = "解析完成",
                    )
                    onQualityChange(q)
                }
                .onFailure { fail(it.message ?: "解析失败") }
        }
    }

    private fun summarize(r: ProbeResult): String {
        val live = if (r.live) "直播" else "点播"
        val dur = if (r.durationSec > 0) {
            val s = r.durationSec.toLong()
            "%d分%02d秒".format(s / 60, s % 60)
        } else ""
        return "$live · 视频${r.videos.size} 音频${r.audios.size} 字幕${r.subs.size}" +
            if (dur.isNotEmpty()) " · $dur" else ""
    }

    fun move(delta: Int) {
        val s = state
        if (s.tracks.isEmpty()) return
        state = s.copy(cursor = (s.cursor + delta).coerceIn(0, s.tracks.size - 1))
    }

    fun toggleCursor() {
        val s = state
        val i = s.cursor
        val t = s.tracks
        if (i !in t.indices) return
        val updated = t.toMutableList()
        updated[i] = updated[i].copy(selected = !updated[i].selected)
        state = s.copy(tracks = updated)
    }

    fun toggleTrackByIndex(index: Int) {
        val s = state
        val t = s.tracks
        if (index !in t.indices) return
        val updated = t.toMutableList()
        updated[index] = updated[index].copy(selected = !updated[index].selected)
        state = s.copy(tracks = updated, cursor = index)
    }

    fun back() {
        state = state.copy(
            screen = Screen.INPUT,
            tracks = emptyList(),
            progress = null,
            status = "就绪",
            error = null,
            showAdvanced = false,
        )
    }

    fun download() {
        val s = state
        val chosen = s.tracks.filter { it.selected }
        if (chosen.isEmpty()) {
            fail("请先选择要下载的轨道")
            return
        }
        val url = s.url.trim()
        scope.launch {
            val outDir = defaultOutDir()
            state = s.copy(
                screen = Screen.DOWNLOADING,
                status = "下载中",
                progress = null,
                logs = emptyList(),
                outDir = outDir.absolutePath,
                error = null,
            )
            runCatching {
                engine.download(
                    url = url,
                    selection = chosen,
                    outDir = outDir,
                    key = s.key,
                    hlsKey = s.hlsKey,
                    hlsIv = s.hlsIv,
                    onLog = { line ->
                        scope.launch {
                            state = state.copy(logs = (state.logs + line).takeLast(200))
                        }
                    },
                    onProgress = { p ->
                        scope.launch { state = state.copy(progress = p) }
                    },
                )
            }.onSuccess {
                state = state.copy(screen = Screen.DONE, status = "完成", progress = null)
            }.onFailure { fail(it.message ?: "下载失败") }
        }
    }

    private fun defaultOutDir(): File {
        val ctx = getApplication<Application>()
        val ext = ctx.getExternalFilesDir(null)
        val dir = File(ext ?: ctx.filesDir, "downloads")
        dir.mkdirs()
        return dir
    }

    private fun fail(msg: String) {
        state = state.copy(screen = Screen.ERROR, status = "出错了", error = msg)
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TuiApp
                return AppViewModel(app) as T
            }
        }
    }
}
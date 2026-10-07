package com.nm3u8dl.tui.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.nm3u8dl.tui.core.Engine
import com.nm3u8dl.tui.core.Progress
import com.nm3u8dl.tui.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class TuiApp : Application() {
    val engine: Engine by lazy { Engine(this) }
}

enum class Screen { INPUT, LOADING, SELECT, DOWNLOADING, DONE, ERROR }

data class UiState(
    val screen: Screen = Screen.INPUT,
    val url: String = "",
    val key: String = "",
    val tracks: List<Track> = emptyList(),
    val cursor: Int = 0,
    val progress: Progress? = null,
    val logs: List<String> = emptyList(),
    val status: String = "就绪",
    val error: String? = null,
    val outDir: String = "",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: Engine = app.engine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state = mutableStateOf(UiState())
        private set

    init {
        prepare()
    }

    private fun prepare() {
        scope.launch {
            state.value = state.value.copy(status = "正在准备内核…")
            runCatching { engine.prepare() }
                .onFailure { fail("内核准备失败：${it.message}") }
                .onSuccess { state.value = state.value.copy(status = "就绪") }
        }
    }

    fun onUrlChange(v: String) {
        state.value = state.value.copy(url = v)
    }

    fun pasteFromClipboard() {
        val ctx = getApplication<Application>()
        val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val text = clip?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
        if (text.isNotEmpty()) {
            state.value = state.value.copy(url = text)
        }
    }

    fun onKeyChange(v: String) {
        state.value = state.value.copy(key = v)
    }

    fun probe() {
        val url = state.value.url.trim()
        if (url.isEmpty()) return
        scope.launch {
            state.value = state.value.copy(
                screen = Screen.LOADING,
                status = "正在解析链接…",
                logs = emptyList(),
                error = null,
            )
            runCatching { engine.probe(url) }
                .onSuccess { result ->
                    val auto = pickDefaults(result)
                    state.value = state.value.copy(
                        screen = Screen.SELECT,
                        tracks = auto,
                        cursor = 0,
                        status = "共 ${result.tracks.size} 条轨道",
                    )
                }
                .onFailure { fail(it.message ?: "解析失败") }
        }
    }

    private fun pickDefaults(result: com.nm3u8dl.tui.core.ProbeResult): List<Track> {
        val v = result.videos.maxByOrNull { it.bandwidth }
        val a = result.audios.firstOrNull()
        return listOfNotNull(v?.copy(selected = true), a?.copy(selected = true))
    }

    fun move(delta: Int) {
        val s = state.value
        if (s.tracks.isEmpty()) return
        state.value = s.copy(cursor = (s.cursor + delta).coerceIn(0, s.tracks.size - 1))
    }

    fun toggleCursor() {
        val s = state.value
        val i = s.cursor
        val t = s.tracks
        if (i !in t.indices) return
        val updated = t.toMutableList()
        updated[i] = updated[i].copy(selected = !updated[i].selected)
        state.value = s.copy(tracks = updated)
    }

    fun autoSelectBest() {
        val s = state.value
        val v = s.tracks.filter { it.kind == "video" }.maxByOrNull { it.bandwidth }
        val a = s.tracks.filter { it.kind == "audio" }.firstOrNull()
        val sub = s.tracks.filter { it.kind == "subtitle" }
            .firstOrNull { it.language.startsWith("zh") || it.language.startsWith("en") }
        val picked = listOfNotNull(
            v?.copy(selected = true),
            a?.copy(selected = true),
            sub?.copy(selected = true),
        )
        state.value = s.copy(tracks = picked, cursor = 0)
    }

    fun back() {
        state.value = state.value.copy(
            screen = Screen.INPUT,
            tracks = emptyList(),
            progress = null,
            status = "就绪",
            error = null,
        )
    }

    fun download() {
        val s = state.value
        val chosen = s.tracks.filter { it.selected }
        if (chosen.isEmpty()) {
            fail("请至少选择一条轨道")
            return
        }
        val url = s.url.trim()
        scope.launch {
            val outDir = defaultOutDir()
            state.value = state.value.copy(
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
                    key = s.key.trim().ifEmpty { null },
                    onLog = { line ->
                        scope.launch {
                            state.value = state.value.copy(
                                logs = (state.value.logs + line).takeLast(200)
                            )
                        }
                    },
                    onProgress = { p ->
                        scope.launch { state.value = state.value.copy(progress = p) }
                    },
                )
            }.onSuccess {
                state.value = state.value.copy(
                    screen = Screen.DONE,
                    status = "完成",
                    progress = null,
                )
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
        state.value = state.value.copy(screen = Screen.ERROR, status = "出错了", error = msg)
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
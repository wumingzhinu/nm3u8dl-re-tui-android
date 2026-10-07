package com.nm3u8dl.tui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel

enum class Mode { INPUT, PROBE, SELECT, DOWNLOAD, DONE }

data class Track(
    val id: Int,
    val group: String,
    val label: String,
    val detail: String,
    val selected: Boolean,
)

data class Progress(
    val done: Int,
    val total: Int,
    val percent: Float,
    val bytes: String,
    val speed: String,
    val eta: String,
)

data class AppState(
    val mode: Mode = Mode.INPUT,
    val url: String = "",
    val lines: List<TermLine> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val cursor: Int = 0,
    val progress: Progress? = null,
    val status: String = "READY",
)

sealed interface Event {
    data class UrlChanged(val value: String) : Event
    data object Submit : Event
    data object MoveUp : Event
    data object MoveDown : Event
    data object ToggleTrack : Event
    data object Cancel : Event
}

class AppViewModel : ViewModel() {
    var state by mutableStateOf(AppState())
        private set

    init {
        append(TermLine.of(TermSpan("N_m3u8DL-RE", Palette.Green, bold = true),
            TermSpan("  ", Palette.Fg),
            TermSpan("TUI", Palette.Cyan, bold = true)))
        append(TermLine.plain("type a stream url, then press enter", Palette.Dim))
        append(TermLine.plain(""))
    }

    fun onEvent(e: Event) {
        when (e) {
            is Event.UrlChanged -> state = state.copy(url = e.value)
            Event.Submit -> submit()
            Event.MoveUp -> state = state.copy(cursor = (state.cursor - 1).coerceAtLeast(0))
            Event.MoveDown -> state = state.copy(
                cursor = (state.cursor + 1).coerceAtMost((state.tracks.size - 1).coerceAtLeast(0))
            )
            Event.ToggleTrack -> toggleTrack()
            Event.Cancel -> state = state.copy(mode = Mode.INPUT, status = "READY")
        }
    }

    private fun submit() {
        val url = state.url.trim()
        if (url.isEmpty()) return
        append(TermLine.of(TermSpan("> ", Palette.Green), TermSpan(url, Palette.Fg)))
        state = state.copy(
            url = "",
            mode = Mode.PROBE,
            status = "PROBING",
        )
        append(TermLine.plain("probe not wired yet", Palette.Amber))
        state = state.copy(mode = Mode.INPUT, status = "READY")
    }

    private fun toggleTrack() {
        if (state.tracks.isEmpty()) return
        val updated = state.tracks.mapIndexed { i, t -> if (i == state.cursor) t.copy(selected = !t.selected) else t }
        state = state.copy(tracks = updated)
    }

    fun append(line: TermLine) {
        state = state.copy(lines = (state.lines + line).takeLast(500))
    }
}

@Composable
fun TerminalApp(vm: AppViewModel) {
    val state = vm.state
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.mode) {
        if (state.mode == Mode.INPUT) keyboard?.show()
    }

    LaunchedEffect(state.url) { input = state.url }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg),
    ) {
        StatusBar(state.status)
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    TerminalGrid(lines = state.lines + inputLines(state, input), followBottom = true)
                }
                if (state.mode == Mode.SELECT) TrackList(state)
                InputLine(
                    value = input,
                    onSubmit = {
                        vm.onEvent(Event.UrlChanged(input))
                        vm.onEvent(Event.Submit)
                        input = ""
                    },
                    onChange = { input = it; vm.onEvent(Event.UrlChanged(it)) },
                )
            }
        }
        KeyBar(
            onUp = { vm.onEvent(Event.MoveUp) },
            onDown = { vm.onEvent(Event.MoveDown) },
            onOk = {
                if (state.mode == Mode.SELECT) vm.onEvent(Event.ToggleTrack)
                else vm.onEvent(Event.Submit)
            },
        )
    }
}

@Composable
private fun StatusBar(status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TermText("N_m3u8DL-RE TUI", Palette.Green, bold = true)
        TermText(status, Palette.Amber, bold = true)
    }
}

@Composable
private fun InputLine(value: String, onSubmit: () -> Unit, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermText("> ", Palette.Green, bold = true)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Palette.Fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(Palette.Green),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrackList(state: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bg)
            .padding(vertical = 4.dp),
    ) {
        state.tracks.forEachIndexed { i, t ->
            val bg = if (i == state.cursor) Palette.SelBg else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                TermText(if (t.selected) "[x] " else "[ ] ", if (t.selected) Palette.Green else Palette.Dim)
                TermText(t.group + "  ", Palette.Cyan)
                TermText(t.label, Palette.Fg)
            }
        }
    }
}

@Composable
private fun KeyBar(onUp: () -> Unit, onDown: () -> Unit, onOk: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bar),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Key("UP", Palette.Cyan, onUp, Modifier.weight(1f))
        Key("DN", Palette.Cyan, onDown, Modifier.weight(1f))
        Key("OK", Palette.Green, onOk, Modifier.weight(2f))
    }
}

@Composable
private fun Key(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TermText(label, color, bold = true)
    }
}

@Composable
fun TermText(text: String, color: Color, bold: Boolean = false) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        ),
        maxLines = 1,
    )
}

private fun inputLines(state: AppState, input: String): List<TermLine> {
    if (state.mode != Mode.INPUT) return emptyList()
    return listOf(
        TermLine.of(
            TermSpan("> ", Palette.Green, bold = true),
            TermSpan(input, Palette.Fg),
            TermSpan("_", Palette.Green),
        )
    )
}

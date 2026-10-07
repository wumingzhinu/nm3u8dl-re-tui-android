package com.nm3u8dl.tui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nm3u8dl.tui.core.Track

@Composable
fun TerminalRoot(vm: AppViewModel) {
    val s = vm.state.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg),
    ) {
        StatusBar(s)
        Box(modifier = Modifier.weight(1f)) {
            when (s.screen) {
                Screen.INPUT, Screen.LOADING -> InputScreen(vm, s)
                Screen.SELECT -> SelectScreen(vm, s)
                Screen.DOWNLOADING -> DownloadScreen(vm, s)
                Screen.DONE, Screen.ERROR -> ResultScreen(vm, s)
            }
        }
        KeyBar(s, vm)
    }
}

@Composable
private fun StatusBar(s: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bar)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TermText("N_m3u8DL-RE 终端", Palette.Green, bold = true)
        TermText(s.status, if (s.screen == Screen.ERROR) Palette.Red else Palette.Amber, bold = true)
    }
}

@Composable
private fun InputScreen(vm: AppViewModel, s: UiState) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        TermLine("把视频页面里的播放链接（.m3u8 / .mpd）粘贴到下面", Palette.Dim)
        TermLine("", Palette.Fg)
        TermLine("用法", Palette.Cyan, bold = true)
        TermLine("  1. 在浏览器里打开视频页面", Palette.Fg)
        TermLine("  2. 复制 .m3u8 或 .mpd 链接", Palette.Fg)
        TermLine("  3. 粘贴到下面的输入框", Palette.Fg)
        TermLine("", Palette.Fg)
        TermLine("遇到加密视频？先在下方填密钥（KID:KEY 或 KEY）", Palette.Dim)
        TermLine("", Palette.Fg)

        InputBox(
            label = "> ",
            labelColor = Palette.Green,
            value = s.url,
            placeholder = "https://example.com/video.m3u8",
            onChange = vm::onUrlChange,
            onSubmit = {
                keyboard?.hide()
                vm.probe()
            },
        )
        TermLine("", Palette.Fg)
        InputBox(
            label = "key ",
            labelColor = Palette.Amber,
            value = s.key,
            placeholder = "留空表示不需要解密",
            onChange = vm::onKeyChange,
            onSubmit = {},
        )
    }
}

@Composable
private fun InputBox(
    label: String,
    labelColor: Color,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermText(label, labelColor, bold = true)
            if (value.isEmpty()) {
                TermText(placeholder, Palette.Dim)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Palette.Fg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                cursorBrush = SolidColor(Palette.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectScreen(vm: AppViewModel, s: UiState) {
    val grouped = remember(s.tracks) {
        s.tracks.groupBy { it.kind }
    }
    val flat = remember(s.tracks) { s.tracks }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            TermLine("用 ↑↓ 选择，空格勾选，然后按「下载」", Palette.Cyan)
            TermLine(
                "已选 ${s.tracks.count { it.selected }} 条 · 视频 ${grouped["video"]?.size ?: 0} / 音频 ${grouped["audio"]?.size ?: 0} / 字幕 ${grouped["subtitle"]?.size ?: 0}",
                Palette.Dim,
            )
        }

        val listState = rememberLazyListState()
        LaunchedEffect(s.cursor) {
            if (s.cursor in flat.indices) listState.scrollToItem(s.cursor)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).background(Palette.Bg),
        ) {
            items(flat) { t ->
                TrackRow(t, t.index == s.cursor)
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, focused: Boolean) {
    val bg = if (focused) Palette.SelBg else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 1.dp),
    ) {
        TermText(if (t.selected) "[x]" else "[ ]", if (t.selected) Palette.Green else Palette.Dim)
        TermText(" ${kindLabel(t.kind)} ", kindColor(t.kind))
        TermText("${pad(t.resolution.ifEmpty { "音频" }, 10)} ", Palette.Fg)
        TermText("${pad(kbps(t), 8)} ", Palette.Amber)
        TermText("${pad(t.language, 8)} ", Palette.Cyan)
        TermText(t.codecs, Palette.Dim)
        if (t.encrypted) TermText(" 🔒", Palette.Red)
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "video" -> "视频"
    "audio" -> "音频"
    "subtitle" -> "字幕"
    else -> "其他"
}

private fun kindColor(kind: String) = when (kind) {
    "video" -> Palette.Magenta
    "audio" -> Palette.Cyan
    "subtitle" -> Palette.Blue
    else -> Palette.Dim
}

private fun kbps(t: Track): String =
    if (t.bandwidth > 0) "${t.bandwidth / 1000}K" else "-"

private fun pad(s: String, n: Int): String =
    if (s.length >= n) s.take(n) else s + " ".repeat(n - s.length)

@Composable
private fun DownloadScreen(vm: AppViewModel, s: UiState) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val p = s.progress
        if (p != null) {
            TermLine("下载进度", Palette.Cyan, bold = true)
            TermLine(bar(p.percent, 32), Palette.Green, bold = true)
            TermLine(
                "${"%.1f".format(p.percent)}%  ${p.done}/${p.total} 分片  ${p.bytes}  ${p.speed}  剩余 ${p.eta}",
                Palette.Fg,
            )
            TermLine("", Palette.Fg)
        } else {
            TermLine("正在启动…", Palette.Amber)
        }
        TermLine("", Palette.Fg)
        LogView(s.logs)
    }
}

private fun bar(percent: Float, width: Int): String {
    val filled = ((percent / 100f) * width).toInt().coerceIn(0, width)
    return "[" + "━".repeat(filled) + " ".repeat(width - filled) + "]"
}

@Composable
private fun LogView(logs: List<String>) {
    val state = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) state.scrollToItem(logs.size - 1)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(logs) { line ->
            TermLine(line, if (line.startsWith("ERR")) Palette.Red else Palette.Dim)
        }
    }
}

@Composable
private fun ResultScreen(vm: AppViewModel, s: UiState) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (s.screen == Screen.ERROR) {
            TermLine("出错了", Palette.Red, bold = true)
            TermLine("", Palette.Fg)
            TermLine(s.error ?: "未知错误", Palette.Fg)
        } else {
            TermLine("下载完成", Palette.Green, bold = true)
            TermLine("", Palette.Fg)
            TermLine("文件保存在：", Palette.Fg)
            TermLine(s.outDir, Palette.Cyan)
        }
        TermLine("", Palette.Fg)
        TermLine("按「返回」继续下载下一个", Palette.Dim)
    }
}

@Composable
private fun KeyBar(s: UiState, vm: AppViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bar),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        when (s.screen) {
            Screen.INPUT, Screen.LOADING -> {
                Key("粘贴", Palette.Cyan, Modifier.weight(1f)) { vm.pasteFromClipboard() }
                Key("开始", Palette.Green, Modifier.weight(2f)) { vm.probe() }
            }
            Screen.SELECT -> {
                Key("↑", Palette.Cyan, Modifier.weight(1f)) { vm.move(-1) }
                Key("↓", Palette.Cyan, Modifier.weight(1f)) { vm.move(1) }
                Key("勾选", Palette.Green, Modifier.weight(1f)) { vm.toggleCursor() }
                Key("最佳", Palette.Magenta, Modifier.weight(1f)) { vm.autoSelectBest() }
                Key("下载", Palette.Amber, Modifier.weight(2f)) { vm.download() }
            }
            Screen.DOWNLOADING -> {
                Key("运行中…", Palette.Dim, Modifier.fillMaxWidth()) {}
            }
            Screen.DONE, Screen.ERROR -> {
                Key("返回", Palette.Green, Modifier.fillMaxWidth()) { vm.back() }
            }
        }
    }
}

@Composable
private fun Key(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .height(46.dp),
        contentAlignment = Alignment.Center,
    ) {
        TermText(label, color, bold = true)
    }
}

@Composable
fun TermLine(text: String, color: Color, bold: Boolean = false) {
    TermText(text, color, bold)
}

@Composable
fun TermText(text: String, color: Color, bold: Boolean = false) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
    )
}
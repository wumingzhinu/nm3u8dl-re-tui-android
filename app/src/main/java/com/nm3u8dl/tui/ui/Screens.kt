package com.nm3u8dl.tui.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText as BasicTextBase
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nm3u8dl.tui.core.Track

@Composable
fun TerminalRoot(vm: AppViewModel) {
    val themeMode = rememberThemeMode()
    val p = paletteFor(themeMode.value)
    val s = vm.state
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg),
    ) {
        TopBar(p, s.status, themeMode.value) {
            toggleThemeMode(themeMode, context)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (s.screen) {
                Screen.INPUT, Screen.LOADING -> InputScreen(vm, s, p)
                Screen.SELECT -> SelectScreen(vm, s, p)
                Screen.DOWNLOADING -> DownloadScreen(vm, s, p)
                Screen.DONE, Screen.ERROR -> ResultScreen(vm, s, p)
            }
        }
        ActionBar(vm, s, p)
    }
}

@Composable
private fun TopBar(p: Palette, status: String, mode: ThemeMode, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BasicText(
                "N_m3u8DL 终端",
                style = if (p.isTerminal) Type.terminalTitle(p) else Type.label(p).copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
            )
            BasicText(
                status,
                style = Type.caption(p),
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(p.surfaceAlt)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            BasicText(
                if (mode == ThemeMode.TERMINAL) "TERMINAL" else "MODERN",
                style = Type.caption(p).copy(color = p.accent),
            )
        }
    }
}

@Composable
private fun InputScreen(vm: AppViewModel, s: UiState, p: Palette) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        StepLabel(p, "第 1 步 / 共 3 步", "粘贴播放链接")

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface)
                .border(1.dp, if (s.url.isBlank()) p.outline else p.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (s.url.isEmpty()) {
                BasicText(
                    "https://example.com/video.m3u8",
                    style = monoStyle(p).copy(color = p.onSurfaceDim),
                )
            }
            BasicTextField(
                value = s.url,
                onValueChange = vm::onUrlChange,
                singleLine = true,
                textStyle = monoStyle(p).copy(color = p.onSurface),
                cursorBrush = SolidColor(p.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    vm.probe()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(p, "从剪贴板粘贴", p.accent, Modifier.weight(1f)) {
                vm.pasteUrl()
            }
            Chip(p, "解析链接", p.primary, Modifier.weight(1f)) {
                keyboard?.hide()
                vm.probe()
            }
        }

        Spacer(Modifier.height(22.dp))

        BasicText("怎么找链接", monoStyle(p).copy(color = p.onSurface))
        Spacer(Modifier.height(8.dp))
        listOf(
            "1. 浏览器打开视频页面，播放一次",
            "2. 按 F12 打开开发者工具，切到 Network/网络",
            "3. 筛选框输入 m3u8 或 mpd",
            "4. 右键复制它的链接，回到这里粘贴",
        ).forEach {
            BasicText(it, monoStyle(p).copy(color = p.onSurfaceDim))
            Spacer(Modifier.height(4.dp))
        }

        if (s.screen == Screen.LOADING) {
            Spacer(Modifier.height(18.dp))
            BasicText("正在解析，请稍候…", monoStyle(p).copy(color = p.warning))
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("加密视频？", Type.caption(p))
            Spacer(Modifier.width(6.dp))
            BasicText(
                if (s.showAdvanced) "收起" else "点这里",
                Type.caption(p).copy(color = p.accent),
                modifier = Modifier.clickable { vm.toggleAdvanced() },
            )
        }

        if (s.showAdvanced) {
            Spacer(Modifier.height(10.dp))
            AdvancedKeys(s, p, vm)
        }
    }
}

@Composable
private fun AdvancedKeys(s: UiState, p: Palette, vm: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            "留空 = 不需要解密。绝大多数视频不用填。",
            Type.caption(p),
        )
        KeyField(p, "DASH/CENC 密钥", s.key, vm::onKeyChange, "KID:KEY 或 KEY")
        KeyField(p, "HLS AES-128 密钥", s.hlsKey, vm::onHlsKeyChange, "清单里没有 key 时才填")
        KeyField(p, "HLS IV（一般不填）", s.hlsIv, vm::onHlsIvChange, "留空则自动用分片序号")
    }
}

@Composable
private fun KeyField(
    p: Palette,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
) {
    Column {
        BasicText(label, Type.caption(p).copy(color = p.onSurface))
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(p.surfaceAlt)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty()) {
                BasicText(hint, monoStyle(p).copy(color = p.onSurfaceDim, fontSize = monoStyle(p).fontSize * 0.9f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = monoStyle(p).copy(color = p.onSurface, fontSize = monoStyle(p).fontSize * 0.9f),
                cursorBrush = SolidColor(p.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectScreen(vm: AppViewModel, s: UiState, p: Palette) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            StepLabel(p, "第 2 步 / 共 3 步", "选择画质")
            Spacer(Modifier.height(4.dp))
            BasicText(s.probeSummary, Type.caption(p))
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Quality.entries.forEach { q ->
                val active = s.quality == q
                val bg by animateColorAsState(
                    if (active) p.primary else p.surface,
                    tween(150),
                )
                val fg = if (active) p.onPrimary else p.onSurface
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .border(
                            1.dp,
                            if (active) Color.Transparent else p.outline,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { vm.onQualityChange(q) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    BasicText(q.label, Type.label(p).copy(color = fg, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val selected = s.tracks.filter { it.selected }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.tracks) { t ->
                TrackCard(t, t.selected, p) { vm.toggleTrackByIndex(t.index) }
            }
            item {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "已选 ${selected.size} 条" + if (selected.isEmpty()) " · 请至少选一条" else "",
                    Type.caption(p),
                )
            }
        }
    }
}

@Composable
private fun TrackCard(t: Track, checked: Boolean, p: Palette, onToggle: () -> Unit) {
    val accent = when (t.kind) {
        "video" -> p.info
        "audio" -> p.accent
        "subtitle" -> p.primary
        else -> p.onSurfaceDim
    }
    val bg by animateColorAsState(
        if (checked) p.surface else p.surface.copy(alpha = 0.55f),
        tween(150),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (checked) 1.5.dp else 1.dp,
                color = if (checked) accent else p.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (checked) accent else Color.Transparent)
                    .border(1.dp, if (checked) accent else p.outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    BasicText("✓", monoStyle(p).copy(color = p.onPrimary, fontSize = monoStyle(p).fontSize * 0.75f))
                }
            }
            Spacer(Modifier.width(10.dp))
            BasicText(kindLabel(t.kind), monoStyle(p).copy(color = accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            Spacer(Modifier.width(10.dp))
            BasicText(
                t.resolution.ifEmpty { kindLabel(t.kind) },
                monoStyle(p).copy(color = p.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            if (t.encrypted) {
                BasicText("需解密", monoStyle(p).copy(color = p.danger))
            }
        }
        Spacer(Modifier.height(5.dp))
        Row {
            val bits = mutableListOf<String>()
            if (t.bandwidth > 0) bits += "${t.bandwidth / 1000} Kbps"
            if (t.language.isNotEmpty() && t.language != "und") bits += t.language
            if (t.codecs.isNotEmpty()) bits += t.codecs.substringBefore('.').uppercase()
            if (t.segments > 0) bits += "${t.segments} 片"
            BasicText(bits.joinToString("  ·  "), monoStyle(p).copy(color = p.onSurfaceDim, fontSize = monoStyle(p).fontSize * 0.85f))
        }
    }
}

@Composable
private fun DownloadScreen(vm: AppViewModel, s: UiState, p: Palette) {
    val prog = s.progress
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        StepLabel(p, "第 3 步 / 共 3 步", "正在下载")

        Spacer(Modifier.height(18.dp))

        val percent = prog?.percent ?: 0f
        val animated by animateFloatAsState(percent, tween(400))

        BasicText(
            if (prog != null) "${"%.1f".format(prog.percent)}%" else "准备中…",
            Type.bigValue(p),
        )

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(p.surfaceAlt),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(p.primary),
            )
        }

        Spacer(Modifier.height(12.dp))

        val info = prog?.let {
            "${it.done}/${it.total} 片   ${it.bytes}   ${it.speed}   剩余 ${it.eta}"
        } ?: ""
        BasicText(info, monoStyle(p).copy(color = p.onSurfaceDim))

        Spacer(Modifier.height(20.dp))

        val logState = rememberLazyListState()
        LaunchedEffect(s.logs.size) {
            if (s.logs.isNotEmpty()) logState.scrollToItem(s.logs.size - 1)
        }
        LazyColumn(state = logState, modifier = Modifier.weight(1f)) {
            items(s.logs) { line ->
                BasicText(
                    line,
                    monoStyle(p).copy(
                        color = if (line.startsWith("ERR")) p.danger else p.onSurfaceDim,
                        fontSize = monoStyle(p).fontSize * 0.85f,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResultScreen(vm: AppViewModel, s: UiState, p: Palette) {
    val ok = s.screen == Screen.DONE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        BasicText(if (ok) "下载完成" else "出错了", Type.bigValue(p).copy(color = if (ok) p.success else p.danger))
        Spacer(Modifier.height(16.dp))
        if (ok) {
            BasicText("文件保存在", Type.caption(p), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.surface)
                    .padding(12.dp),
            ) {
                BasicText(s.outDir, monoStyle(p).copy(color = p.accent), textAlign = TextAlign.Center)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.surface)
                    .padding(12.dp),
            ) {
                BasicText(
                    s.error ?: "未知错误",
                    monoStyle(p).copy(color = p.danger),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        BasicText("点下方「返回」继续下一个", Type.caption(p))
    }
}

@Composable
private fun ActionBar(vm: AppViewModel, s: UiState, p: Palette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (s.screen) {
            Screen.INPUT -> {
                Chip(p, "粘贴", p.accent, Modifier.weight(1f)) { vm.pasteUrl() }
                Chip(p, "开始解析", p.primary, Modifier.weight(1.4f)) { vm.probe() }
            }
            Screen.LOADING -> {
                BasicText("解析中…", monoStyle(p).copy(color = p.warning), modifier = Modifier.weight(1f))
            }
            Screen.SELECT -> {
                Chip(p, "返回", p.onSurfaceDim, Modifier.weight(1f)) { vm.back() }
                Chip(
                    p,
                    "下载 ${s.tracks.count { it.selected }} 条",
                    p.primary,
                    Modifier.weight(2f),
                ) { vm.download() }
            }
            Screen.DOWNLOADING -> {
                BasicText("正在下载，请勿关闭应用", monoStyle(p).copy(color = p.warning), modifier = Modifier.weight(1f))
            }
            Screen.DONE, Screen.ERROR -> {
                Chip(p, "返回", p.primary, Modifier.weight(1f)) { vm.back() }
            }
        }
    }
}

@Composable
private fun StepLabel(p: Palette, step: String, title: String) {
    Column {
        BasicText(step, monoStyle(p).copy(color = p.accent, fontSize = monoStyle(p).fontSize * 0.85f))
        Spacer(Modifier.height(2.dp))
        BasicText(
            title,
            if (p.isTerminal) Type.terminalTitle(p) else Type.label(p).copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = p.onSurface,
            ),
        )
    }
}

@Composable
private fun Chip(p: Palette, label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            Type.label(p).copy(
                color = if (p.isTerminal || accent == p.onSurfaceDim) p.bg else Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
        )
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "video" -> "视频"
    "audio" -> "音频"
    "subtitle" -> "字幕"
    else -> "其他"
}

@Composable
private fun monoStyle(p: Palette): TextStyle = TextStyle(
    fontFamily = if (p.isTerminal) Type.mono else Type.sans,
    fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
    color = p.onSurface,
)

@Composable
private fun BasicText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    BasicTextBase(
        text = text,
        style = style.copy(textAlign = textAlign),
        maxLines = 4,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
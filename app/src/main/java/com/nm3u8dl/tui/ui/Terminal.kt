package com.nm3u8dl.tui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0C0C0C)
    val Surface = Color(0xFF161B22)
    val Fg = Color(0xFFC9D1D9)
    val Dim = Color(0xFF6E7681)
    val Green = Color(0xFF4AF626)
    val Cyan = Color(0xFF3FD0D4)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF5555)
    val Blue = Color(0xFF79B8FF)
    val Magenta = Color(0xFFBC8CFF)
    val SelBg = Color(0xFF2D4A22)
    val Bar = Color(0xFF21262D)
}

data class TermSpan(
    val text: String,
    val fg: Color = Palette.Fg,
    val bg: Color? = null,
    val bold: Boolean = false,
) {
    fun annotate(): AnnotatedString = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = fg,
                background = bg ?: Color.Transparent,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            )
        ) { append(text) }
    }
}

data class TermLine(val spans: List<TermSpan>) {
    val text: String get() = spans.joinToString("") { it.text }

    companion object {
        fun of(vararg spans: TermSpan) = TermLine(spans.toList())
        fun plain(text: String, fg: Color = Palette.Fg) = TermLine(listOf(TermSpan(text, fg)))
    }
}

@Composable
fun TerminalGrid(
    lines: List<TermLine>,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier,
    followBottom: Boolean = true,
) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size, followBottom) {
        if (followBottom && lines.isNotEmpty()) state.scrollToItem(lines.size - 1)
    }
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Bg),
    ) {
        items(lines) { line ->
            BasicText(
                text = if (line.spans.isEmpty()) {
                    AnnotatedString(" ")
                } else {
                    buildAnnotatedString { line.spans.forEach { append(it.annotate()) } }
                },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value + 5).sp,
                    color = Palette.Fg,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

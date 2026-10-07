package com.nm3u8dl.tui.ui

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode { TERMINAL, MODERN }

/** 两套主题共用的语义色槽，组件只引用语义，不写死颜色。 */
class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceDim: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val highlight: Color,
    val isTerminal: Boolean,
)

private val TerminalPalette = Palette(
    bg = Color(0xFF0B0E14),
    surface = Color(0xFF131823),
    surfaceAlt = Color(0xFF1B2230),
    outline = Color(0xFF2A3444),
    onSurface = Color(0xFFD7DEE9),
    onSurfaceDim = Color(0xFF7B8798),
    primary = Color(0xFF7EE787),
    onPrimary = Color(0xFF06110A),
    accent = Color(0xFF79C0FF),
    success = Color(0xFF3FB950),
    warning = Color(0xFFD29922),
    danger = Color(0xFFF85149),
    info = Color(0xFFA371F7),
    highlight = Color(0xFF1F6F43),
    isTerminal = true,
)

private val ModernPalette = Palette(
    bg = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDF0F7),
    outline = Color(0xFFDDE1EA),
    onSurface = Color(0xFF14181F),
    onSurfaceDim = Color(0xFF6A7280),
    primary = Color(0xFF2E6BE6),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF0F9B8E),
    success = Color(0xFF1E9E62),
    warning = Color(0xFFD97706),
    danger = Color(0xFFD64545),
    info = Color(0xFF7C4DDB),
    highlight = Color(0xFFDCE7FB),
    isTerminal = false,
)

@Composable
fun rememberThemeMode(): MutableState<ThemeMode> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { mutableStateOf(ThemeMode.valueOf(loadThemePref(ctx))) }
}

fun toggleThemeMode(state: MutableState<ThemeMode>, context: Context) {
    val next = if (state.value == ThemeMode.TERMINAL) ThemeMode.MODERN else ThemeMode.TERMINAL
    state.value = next
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_THEME, next.name).apply()
}

private fun loadThemePref(context: Context): String = runCatching {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_THEME, ThemeMode.TERMINAL.name).orEmpty()
}.getOrDefault(ThemeMode.TERMINAL.name)

private const val PREFS = "ui"
private const val KEY_THEME = "theme_mode"

@Composable
fun paletteFor(mode: ThemeMode): Palette =
    if (mode == ThemeMode.TERMINAL) TerminalPalette else ModernPalette

object Type {
    val mono = FontFamily.Monospace
    val sans = FontFamily.SansSerif

    fun terminalTitle(p: Palette) = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = p.primary,
    )

    fun terminalBody(p: Palette) = TextStyle(
        fontFamily = mono,
        fontSize = 13.sp,
        color = p.onSurface,
    )

    fun label(p: Palette) = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = p.onSurface,
    )

    fun caption(p: Palette) = TextStyle(
        fontFamily = sans,
        fontSize = 12.sp,
        color = p.onSurfaceDim,
    )

    fun bigValue(p: Palette) = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = p.onSurface,
    )
}
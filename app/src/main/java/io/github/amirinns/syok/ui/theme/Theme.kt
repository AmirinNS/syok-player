package io.github.amirinns.syok.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Direction A: "Night Drive" Tokens (from design/car-player/DECISIONS.md)
val SyokNightBase = Color(0xFF0A0D14)
val SyokNightSurface = Color(0xFF121722)
val SyokNightCard = Color(0xFF182030)
val SyokNightCardHover = Color(0xFF222D42)
val SyokAmberAccent = Color(0xFFFF9F0A)       // 590nm warm amber glow (10.8:1 AAA)
val SyokAmberContrast = Color(0xFF000000)
val SyokTextHigh = Color(0xFFFFFFFF)          // 18.5:1 AAA
val SyokTextMed = Color(0xFF94A3B8)
val SyokTextLow = Color(0xFF64748B)
val SyokBorder = Color(0xFF243044)
val SyokBorderSubtle = Color(0xFF1C2637)

private val DarkColorScheme = darkColorScheme(
    primary = SyokAmberAccent,
    onPrimary = SyokAmberContrast,
    primaryContainer = SyokNightCard,
    onPrimaryContainer = SyokAmberAccent,
    background = SyokNightBase,
    onBackground = SyokTextHigh,
    surface = SyokNightSurface,
    onSurface = SyokTextHigh,
    surfaceVariant = SyokNightCard,
    onSurfaceVariant = SyokTextMed,
    outline = SyokBorder,
    outlineVariant = SyokBorderSubtle
)

@Composable
fun SyokTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

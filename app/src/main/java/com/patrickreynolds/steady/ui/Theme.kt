package com.patrickreynolds.steady.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Good / warning / crash colours, kept apart from the brand colour. */
@Immutable
data class StateColors(val ok: Color, val okSoft: Color, val warn: Color, val warnSoft: Color, val stop: Color, val stopSoft: Color, val muted: Color)

private val LightState = StateColors(
    ok = Color(0xFF2A7748), okSoft = Color(0xFFE0F0E6),
    warn = Color(0xFF9A6200), warnSoft = Color(0xFFF7ECD3),
    stop = Color(0xFFAB3636), stopSoft = Color(0xFFF6E0E0),
    muted = Color(0xFF546270),
)
private val DarkState = StateColors(
    ok = Color(0xFF6CC48F), okSoft = Color(0xFF16301F),
    warn = Color(0xFFE3B558), warnSoft = Color(0xFF33280E),
    stop = Color(0xFFEA8585), stopSoft = Color(0xFF3A1C1D),
    muted = Color(0xFF9BA8B4),
)
/** E-ink: black ink on white paper. Meaning comes from words, ticks, borders and patterns, never colour. */
private val EinkState = StateColors(
    ok = Color.Black, okSoft = Color.White,
    warn = Color.Black, warnSoft = Color.White,
    stop = Color.Black, stopSoft = Color.White,
    muted = Color.Black,
)

val LocalStateColors = staticCompositionLocalOf { LightState }

/** True in E-ink mode: screens draw thick borders, patterns and no motion. */
val LocalEink = staticCompositionLocalOf { false }

/** Border width for panels and buttons: thicker on e-ink, where thin grey lines disappear. */
val panelBorder: Dp @Composable get() = if (LocalEink.current) 3.dp else 1.dp

private val Light = lightColorScheme(
    primary = Color(0xFF2C5A86), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EBF4), onPrimaryContainer = Color(0xFF16324D),
    background = Color(0xFFF3F5F7), onBackground = Color(0xFF16202A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16202A),
    surfaceVariant = Color(0xFFE6EBF0), onSurfaceVariant = Color(0xFF546270),
    outline = Color(0xFFD7DEE5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FB6DE), onPrimary = Color(0xFF0E2033),
    primaryContainer = Color(0xFF1D2C3B), onPrimaryContainer = Color(0xFFD6E5F4),
    background = Color(0xFF10151A), onBackground = Color(0xFFE2E8EE),
    surface = Color(0xFF172029), onSurface = Color(0xFFE2E8EE),
    surfaceVariant = Color(0xFF1F2A35), onSurfaceVariant = Color(0xFF9BA8B4),
    outline = Color(0xFF2A3541),
)
private val Eink = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    primaryContainer = Color.White, onPrimaryContainer = Color.Black,
    secondaryContainer = Color.Black, onSecondaryContainer = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color.White, onSurfaceVariant = Color.Black,
    surfaceContainerHighest = Color.White, surfaceContainerHigh = Color.White, surfaceContainer = Color.White,
    outline = Color.Black, outlineVariant = Color.Black,
)

/** Larger than Material's defaults: easier to read on a foggy morning. */
private fun type(eink: Boolean) = Typography().let { t ->
    // E-ink: a size step up and heavier body text, which survives the panel's low contrast.
    val body = if (eink) FontWeight.Medium else FontWeight.Normal
    t.copy(
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = if (eink) 20.sp else 19.sp, lineHeight = 29.sp, fontWeight = body),
        bodyMedium = TextStyle(fontSize = if (eink) 18.sp else 17.sp, lineHeight = 26.sp, fontWeight = body),
        bodySmall = TextStyle(fontSize = if (eink) 16.sp else 15.sp, lineHeight = 22.sp, fontWeight = body),
        labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    )
}

enum class Look { LIGHT, DARK, EINK }

/** Turns the "theme" setting into what to draw, following the phone for "system". */
@Composable
fun lookFor(theme: String): Look = when (theme) {
    "light" -> Look.LIGHT
    "dark" -> Look.DARK
    "eink" -> Look.EINK
    else -> if (isSystemInDarkTheme()) Look.DARK else Look.LIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteadyTheme(look: Look, content: @Composable () -> Unit) {
    val eink = look == Look.EINK
    CompositionLocalProvider(
        LocalStateColors provides when (look) { Look.LIGHT -> LightState; Look.DARK -> DarkState; Look.EINK -> EinkState },
        LocalEink provides eink,
        // No ripples on e-ink: every animation frame is a slow, flickery screen refresh.
        *(if (eink) arrayOf(LocalRippleConfiguration provides null) else emptyArray()),
    ) {
        MaterialTheme(
            colorScheme = when (look) { Look.LIGHT -> Light; Look.DARK -> Dark; Look.EINK -> Eink },
            typography = type(eink),
            content = content,
        )
    }
}

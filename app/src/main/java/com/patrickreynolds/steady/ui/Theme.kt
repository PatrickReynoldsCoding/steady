package com.patrickreynolds.steady.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

val LocalStateColors = staticCompositionLocalOf { LightState }

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

/** Larger than Material's defaults: easier to read on a foggy morning. */
private val Type = Typography().let { t ->
    t.copy(
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp),
        bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
        bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun SteadyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalStateColors provides if (dark) DarkState else LightState) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}

package dev.voltra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.voltra.ThemeMode

val VoltraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBCF36B),
    onPrimary = Color(0xFF142204),
    primaryContainer = Color(0xFF30471B),
    onPrimaryContainer = Color(0xFFD9FB9F),
    secondary = Color(0xFFA0D59C),
    onSecondary = Color(0xFF102A14),
    secondaryContainer = Color(0xFF263B2B),
    onSecondaryContainer = Color(0xFFCCEFCA),
    tertiary = Color(0xFF8FD9EC),
    onTertiary = Color(0xFF003542),
    tertiaryContainer = Color(0xFF1A424E),
    onTertiaryContainer = Color(0xFFC2EFFF),
    background = Color(0xFF101713),
    onBackground = Color(0xFFEEF4EA),
    surface = Color(0xFF101713),
    onSurface = Color(0xFFEEF4EA),
    surfaceVariant = Color(0xFF1B2620),
    onSurfaceVariant = Color(0xFF9CAB9F),
    surfaceContainerLowest = Color(0xFF0B110E),
    surfaceContainerLow = Color(0xFF151E19),
    surfaceContainer = Color(0xFF1B2620),
    surfaceContainerHigh = Color(0xFF233029),
    surfaceContainerHighest = Color(0xFF2C3B33),
    outline = Color(0xFF495A4F),
    outlineVariant = Color(0xFF2E3D34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val VoltraLightColorScheme = lightColorScheme(
    primary = Color(0xFF3A6B13),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7F289),
    onPrimaryContainer = Color(0xFF0F2200),
    secondary = Color(0xFF47644A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4EED5),
    onSecondaryContainer = Color(0xFF0B200F),
    tertiary = Color(0xFF1B6678),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFEBF9),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF4F7F3),
    onBackground = Color(0xFF151D17),
    surface = Color(0xFFF4F7F3),
    onSurface = Color(0xFF151D17),
    surfaceVariant = Color(0xFFE4ECE5),
    onSurfaceVariant = Color(0xFF4A594D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF3ED),
    surfaceContainer = Color(0xFFE7EFE7),
    surfaceContainerHigh = Color(0xFFDFE8DF),
    surfaceContainerHighest = Color(0xFFD6E1D7),
    outline = Color(0xFF728275),
    outlineVariant = Color(0xFFC4D0C5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Immutable
data class VoltraExtendedColors(
    val warning: Color,
    val chartLevel: Color,
    val chartTemperature: Color,
    val chartCurrent: Color,
    val chartVoltage: Color,
    val gaugeTrack: Color,
    val badgeContainer: Color,
    val badgeContent: Color,
)

private val DarkExtendedColors = VoltraExtendedColors(
    warning = Color(0xFFFFC182),
    chartLevel = Color(0xFFBCF36B),
    chartTemperature = Color(0xFFFFB47D),
    chartCurrent = Color(0xFF8FD9EC),
    chartVoltage = Color(0xFFC4BAF8),
    gaugeTrack = Color(0xFF314037),
    badgeContainer = Color(0xFF2B3F24),
    badgeContent = Color(0xFFBCF36B),
)

private val LightExtendedColors = VoltraExtendedColors(
    warning = Color(0xFF9A5200),
    chartLevel = Color(0xFF3A6B13),
    chartTemperature = Color(0xFFC85D16),
    chartCurrent = Color(0xFF1B6678),
    chartVoltage = Color(0xFF5D4EA8),
    gaugeTrack = Color(0xFFCEDACD),
    badgeContainer = Color(0xFFD4F1A6),
    badgeContent = Color(0xFF1E3B04),
)

val LocalVoltraColors = staticCompositionLocalOf { DarkExtendedColors }

@Composable
fun shouldUseDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun VoltraTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VoltraDarkColorScheme else VoltraLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalVoltraColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

package app.sip.water.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF0B6BCB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF00A2B8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDF3F8),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = Color(0xFFF2994A),
    tertiaryContainer = Color(0xFFFFE3CC),
    onTertiaryContainer = Color(0xFF3A1E00),
    background = Color(0xFFF7F9FF),
    surface = Color(0xFFF7F9FF),
    surfaceContainerLow = Color(0xFFF0F4FC),
    surfaceContainer = Color(0xFFEAEFF8),
    surfaceContainerHigh = Color(0xFFE4EAF4),
    surfaceVariant = Color(0xFFDEE5F0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF6BD8E8),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFCDF3F8),
    tertiary = Color(0xFFFFB77C),
    tertiaryContainer = Color(0xFF6B3A00),
    onTertiaryContainer = Color(0xFFFFE3CC),
    background = Color(0xFF0F1419),
    surface = Color(0xFF0F1419),
    surfaceContainerLow = Color(0xFF151B22),
    surfaceContainer = Color(0xFF1A2129),
    surfaceContainerHigh = Color(0xFF212933),
    surfaceVariant = Color(0xFF2A3440),
)

@Composable
fun SipTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

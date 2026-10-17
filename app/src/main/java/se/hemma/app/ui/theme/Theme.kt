package se.hemma.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import se.hemma.app.domain.PriceLevel

object LevelColors {
    val Cheap = Color(0xFF1E9E5A)
    val Normal = Color(0xFFE3A008)
    val Expensive = Color(0xFFD64545)
    val Unknown = Color(0xFF8A9692)
}

fun PriceLevel.color(): Color = when (this) {
    PriceLevel.CHEAP -> LevelColors.Cheap
    PriceLevel.NORMAL -> LevelColors.Normal
    PriceLevel.EXPENSIVE -> LevelColors.Expensive
    PriceLevel.UNKNOWN -> LevelColors.Unknown
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7A6C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFEFE4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF52635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8E3),
    onSecondaryContainer = Color(0xFF0F1F1C),
    tertiary = Color(0xFFB4690E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF3F6F5),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF404946),
    surfaceContainer = Color(0xFFE8EFEC),
    outline = Color(0xFF707975),
    outlineVariant = Color(0xFFC0C9C5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFBFEFE4),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF1A2220),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainer = Color(0xFF161D1B),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
)

@Composable
fun HemmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

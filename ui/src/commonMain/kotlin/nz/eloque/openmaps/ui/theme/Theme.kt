package nz.eloque.openmaps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import nz.eloque.compose_kit.theme.AppTheme
import nz.eloque.compose_kit.theme.dynamicColorSchemeOrNull

@Composable
fun OpenMapsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = dynamicColorSchemeOrNull(dark) ?: if (dark) darkColorScheme() else lightColorScheme()
    AppTheme(colorScheme = colorScheme, content = content)
}

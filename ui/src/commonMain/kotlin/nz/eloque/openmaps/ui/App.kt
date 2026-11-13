package nz.eloque.openmaps.ui

import androidx.compose.runtime.Composable
import nz.eloque.openmaps.ui.home.HomeScreen
import nz.eloque.openmaps.ui.theme.OpenMapsTheme

@Composable
fun App() {
    OpenMapsTheme {
        HomeScreen()
    }
}

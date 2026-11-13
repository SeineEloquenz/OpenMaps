package nz.eloque.openmaps.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.openmaps.map.OpenMap
import nz.eloque.openmaps.map.rememberMapController

private val FabMargin = 16.dp
private val FabSize = 56.dp

@Composable
fun HomeScreen() {
    val controller = rememberMapController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val mapPadding =
        PaddingValues(
            start = safe.calculateStartPadding(direction),
            top = safe.calculateTopPadding(),
            end = safe.calculateEndPadding(direction),
            bottom = safe.calculateBottomPadding() + FabSize + FabMargin,
        )

    Box(Modifier.fillMaxSize()) {
        OpenMap(controller, Modifier.fillMaxSize(), contentPadding = mapPadding)

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(FabMargin)) {
            FloatingActionButton(
                modifier = Modifier.align(Alignment.BottomEnd),
                onClick = {
                    if (!controller.isLocationPermissionGranted) {
                        controller.requestLocationPermission()
                    } else {
                        scope.launch {
                            if (!controller.centerOnUser()) snackbar.showSnackbar("Waiting for location")
                        }
                    }
                },
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

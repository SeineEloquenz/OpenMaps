package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable

/** Returns a function that asks for permission to show notifications if needed, then reports whether it is granted. */
@Composable
expect fun rememberNotificationPermissionRequest(): (onResult: (Boolean) -> Unit) -> Unit

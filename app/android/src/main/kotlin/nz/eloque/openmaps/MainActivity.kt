package nz.eloque.openmaps

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.navigation.guidanceActive
import nz.eloque.openmaps.ui.App
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val files: AndroidFiles by inject()

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            lifecycleScope.launch {
                awaitMapBack()
                files.onPicked(uri)
            }
        }
    private val links = Channel<String>(Channel.BUFFERED)
    private val linkFlow = links.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        files.launchPicker = { picker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "application/xml", "text/xml")) }
        if (savedInstanceState == null) handle(intent)
        setContent { App(BuildConfig.VERSION_NAME, debug = BuildConfig.DEBUG, links = linkFlow) }
        val active = guidanceActive(get(), get())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                active.collect { navigating ->
                    if (navigating) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        files.launchPicker = null
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val file = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && it.scheme in FileSchemes }
        if (file != null) {
            lifecycleScope.launch {
                awaitMapBack()
                files.open(file)
            }
        } else {
            intent.link()?.let(links::trySend)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }
}

/**
 * Files arrive before the activity resumes, while the map is still being restored, and drawing a track on it then can
 * leave the map stuck. Waiting for the activity to resume and a moment more avoids that.
 */
private suspend fun ComponentActivity.awaitMapBack() {
    withResumed {}
    delay(MAP_SETTLE_MILLIS)
}

private const val MAP_SETTLE_MILLIS = 500L

private fun Intent.link(): String? = dataString.takeIf { action == Intent.ACTION_VIEW }

private val FileSchemes = setOf("content", "file")

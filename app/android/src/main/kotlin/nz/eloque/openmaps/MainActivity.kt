package nz.eloque.openmaps

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.navigation.guidanceActive
import nz.eloque.openmaps.ui.App
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    private val links = Channel<String>(Channel.BUFFERED)
    private val linkFlow = links.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) intent.link()?.let(links::trySend)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.link()?.let(links::trySend)
    }
}

private fun Intent.link(): String? = dataString.takeIf { action == Intent.ACTION_VIEW }

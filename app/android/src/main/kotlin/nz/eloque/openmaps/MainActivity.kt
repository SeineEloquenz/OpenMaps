package nz.eloque.openmaps

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.navigation.guidanceActive
import nz.eloque.openmaps.ui.App
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(BuildConfig.VERSION_NAME, debug = BuildConfig.DEBUG) }
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
}

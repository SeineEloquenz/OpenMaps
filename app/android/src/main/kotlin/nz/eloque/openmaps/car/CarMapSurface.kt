package nz.eloque.openmaps.car

import android.content.res.Configuration
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import nz.eloque.openmaps.map.CarMap
import org.maplibre.compose.map.AndroidMapPresentation

/** Draws [map] on the car's screen and passes the driver's gestures on to it. */
internal class CarMapSurface(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
    private val map: CarMap,
    private val onGesture: () -> Unit,
) : SurfaceCallback,
    DefaultLifecycleObserver {
    private val presentation =
        AndroidMapPresentation(
            context = carContext,
            state = map.state,
            lifecycle = lifecycle,
            configuration = carConfiguration(carContext.resources.configuration),
        )
    private val appManager = carContext.getCarService(AppManager::class.java)
    private var surface: Surface? = null
    private var binding: AndroidMapPresentation.SurfaceBinding? = null
    private var width = 0
    private var height = 0
    private var density = 1f
    private var visibleArea = Rect()
    private var stableArea = Rect()
    private var closed = false

    val failed: Boolean get() = presentation.failure != null

    init {
        lifecycle.addObserver(this)
        appManager.setSurfaceCallback(this)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val incoming = surfaceContainer.surface
        if (closed || failed) {
            if (incoming !== surface) incoming?.release()
            return
        }
        if (incoming == null ||
            !incoming.isValid ||
            surfaceContainer.width <= 0 ||
            surfaceContainer.height <= 0 ||
            surfaceContainer.dpi <= 0
        ) {
            val previous = surface
            try {
                releaseSurface()
            } finally {
                if (incoming !== previous) incoming?.release()
            }
            return
        }
        width = surfaceContainer.width
        height = surfaceContainer.height
        density = surfaceContainer.dpi / 160f
        if (incoming === surface) {
            binding?.update(width, height, density)
        } else {
            // The host may hand over a new Surface object for the same buffer, so the old one must be let go first.
            try {
                releaseSurface()
                binding = presentation.attachSurface(incoming, width, height, density)
                surface = incoming
            } catch (error: Throwable) {
                incoming.release()
                throw error
            }
        }
        updateInsets()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        val previous = surface
        val destroyed = surfaceContainer.surface
        try {
            releaseSurface()
        } finally {
            if (destroyed !== previous) destroyed?.release()
        }
        visibleArea.setEmpty()
        stableArea.setEmpty()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        updateInsets()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = Rect(stableArea)
        updateInsets()
    }

    override fun onScroll(
        distanceX: Float,
        distanceY: Float,
    ) {
        if (binding == null) return
        onGesture()
        map.state.panBy(logical(-distanceX, -distanceY))
    }

    override fun onFling(
        velocityX: Float,
        velocityY: Float,
    ) {
        if (binding == null) return
        onGesture()
        map.state.fling(logical(velocityX, velocityY))
    }

    override fun onScale(
        focusX: Float,
        focusY: Float,
        scaleFactor: Float,
    ) {
        if (binding == null) return
        onGesture()
        map.state.scaleBy(scaleFactor.toDouble(), if (focusX < 0f || focusY < 0f) null else logical(focusX, focusY))
    }

    fun updateConfiguration(configuration: Configuration) {
        if (!closed && !failed) presentation.updateConfiguration(carConfiguration(configuration))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        closed = true
        try {
            appManager.setSurfaceCallback(null)
        } finally {
            try {
                presentation.close()
                releaseSurface()
                map.close()
            } finally {
                owner.lifecycle.removeObserver(this)
            }
        }
    }

    private fun logical(
        x: Float,
        y: Float,
    ) = DpOffset((x / density).dp, (y / density).dp)

    private fun releaseSurface() {
        binding?.close()
        binding = null
        surface?.release()
        surface = null
    }

    /** Keeps the route and position clear of the car's own controls, using the stable area so the camera does not jump. */
    private fun updateInsets() {
        if (closed || surface == null || failed) return
        val area = Rect(if (!stableArea.isEmpty) stableArea else visibleArea)
        presentation.viewportInsets =
            if (area.isEmpty || !area.intersect(0, 0, width, height)) {
                PaddingValues(0.dp)
            } else {
                PaddingValues.Absolute(
                    left = (area.left / density).dp,
                    top = (area.top / density).dp,
                    right = ((width - area.right) / density).dp,
                    bottom = ((height - area.bottom) / density).dp,
                )
            }
    }

    private fun carConfiguration(configuration: Configuration): Configuration =
        Configuration(configuration).apply {
            val night = if (carContext.isDarkMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
}

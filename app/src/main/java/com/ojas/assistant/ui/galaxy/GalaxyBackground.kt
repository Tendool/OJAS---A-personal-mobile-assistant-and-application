package com.ojas.assistant.ui.galaxy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max

/**
 * The galaxy, wired into Compose.
 *
 * The GL surface sits at the bottom of the view hierarchy and every screen is drawn on
 * top of it, so there is exactly one renderer for the whole app rather than one per
 * screen. It is paused with the host lifecycle, which is what stops it costing anything
 * while the user is in another app.
 */
@Composable
fun GalaxyBackground(
    modifier: Modifier = Modifier,
    starCount: Int = GalaxyView.DEFAULT_STARS,
    reduceMotion: Boolean = false,
    interactive: Boolean = true,
    vignette: Boolean = true
) {
    val context = LocalContext.current
    val galaxy = remember { GalaxyView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, galaxy) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> galaxy.onResume()
                Lifecycle.Event.ON_PAUSE -> galaxy.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { galaxy },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setStarCount(starCount)
                view.setReducedMotion(reduceMotion)
                view.interactive = interactive
                view.setTargetFps(if (reduceMotion) 0 else 30)
            }
        )

        if (vignette) {
            // A soft radial darkening pulls focus to the core and keeps light text
            // legible over the bright arms near the screen edges.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val radius = max(size.width, size.height) * 0.72f
                        val brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.55f to Color(0x2202040A),
                                1.0f to Color(0xAA02040A)
                            ),
                            center = Offset(size.width / 2f, size.height * 0.46f),
                            radius = radius
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush)
                        }
                    }
            )
        }
    }
}

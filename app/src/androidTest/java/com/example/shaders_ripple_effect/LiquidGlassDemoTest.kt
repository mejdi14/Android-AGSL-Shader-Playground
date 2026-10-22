package com.example.shaders_ripple_effect

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders on-device because RuntimeShader compilation is deferred until first draw. */
@RunWith(AndroidJUnit4::class)
class LiquidGlassDemoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glassRefractsWhileDraggingAndDisablesDispersionAfterRelease() {
        compose.setContent { AppleLiquidGlassDemoScreen() }
        val resting = screenshot("glass-resting")

        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(90f, 60f))
        }
        val dragging = screenshot("glass-dragging")
        assertFalse("Dragging must move the refracting glass", resting.sameAs(dragging))
        compose.onRoot().performTouchInput { up() }
        val released = screenshot("glass-released")
        assertFalse("Dispersion should switch off after release", dragging.sameAs(released))
    }

    private fun screenshot(name: String): Bitmap {
        compose.waitForIdle()
        // Allow the recorded backdrop and the RenderEffect to reach the render thread.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}

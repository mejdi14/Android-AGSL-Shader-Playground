package com.example.shaderrippleeffect

import com.example.shaderrippleeffect.internal.LiquidGlassMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassMathTest {
    @Test
    fun roundedRectangleSdf_hasExpectedSignAndBoundary() {
        assertTrue(LiquidGlassMath.roundedRectSdf(0f, 0f, 100f, 60f, 24f) < 0f)
        assertEquals(
            0f,
            LiquidGlassMath.roundedRectSdf(100f, 0f, 100f, 60f, 24f),
            0.0001f
        )
        assertTrue(LiquidGlassMath.roundedRectSdf(110f, 0f, 100f, 60f, 24f) > 0f)
    }

    @Test
    fun bevelHeight_isContinuousAndFlatAfterRadius() {
        val radius = 24f
        assertEquals(0f, LiquidGlassMath.bevelHeight(0f, radius), 0f)
        assertTrue(LiquidGlassMath.bevelHeight(6f, radius) > 0f)
        assertEquals(radius, LiquidGlassMath.bevelHeight(radius, radius), 0f)
        assertEquals(radius, LiquidGlassMath.bevelHeight(radius * 2f, radius), 0f)
    }

    @Test
    fun bk7Dispersion_bendsBlueMoreThanGreenAndRedLess() {
        val red = LiquidGlassMath.dispersionScale(LiquidGlassMath.RED_IOR, 0.8f)
        val green = LiquidGlassMath.dispersionScale(LiquidGlassMath.GREEN_IOR, 0.8f)
        val blue = LiquidGlassMath.dispersionScale(LiquidGlassMath.BLUE_IOR, 0.8f)

        assertTrue(red < green)
        assertEquals(1f, green, 0.0001f)
        assertTrue(blue > green)
        assertTrue(blue - red < 0.05f)
    }
}

package com.example.shaderrippleeffect.internal

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** CPU mirror of the small optical primitives used by the liquid-glass AGSL shaders. */
internal object LiquidGlassMath {
    const val RED_IOR = 1.5143f
    const val GREEN_IOR = 1.5168f
    const val BLUE_IOR = 1.5224f

    fun roundedRectSdf(
        x: Float,
        y: Float,
        halfWidth: Float,
        halfHeight: Float,
        radius: Float
    ): Float {
        val clampedRadius = min(radius, min(halfWidth, halfHeight))
        val qx = kotlin.math.abs(x) - halfWidth + clampedRadius
        val qy = kotlin.math.abs(y) - halfHeight + clampedRadius
        val outsideX = max(qx, 0f)
        val outsideY = max(qy, 0f)
        return min(max(qx, qy), 0f) + sqrt(outsideX * outsideX + outsideY * outsideY) - clampedRadius
    }

    fun bevelHeight(distanceInside: Float, bevelRadius: Float): Float {
        if (bevelRadius <= 0f || distanceInside <= 0f) return 0f
        if (distanceInside >= bevelRadius) return bevelRadius
        return sqrt(distanceInside * (2f * bevelRadius - distanceInside))
    }

    fun dispersionScale(ior: Float, exaggeration: Float): Float =
        1f + ((ior - GREEN_IOR) / (GREEN_IOR - 1f)) * exaggeration * 3f
}

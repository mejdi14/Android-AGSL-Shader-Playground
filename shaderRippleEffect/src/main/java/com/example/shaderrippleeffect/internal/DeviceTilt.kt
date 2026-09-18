package com.example.shaderrippleeffect.internal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Device tilt in radians, smoothed and measured *relative to how the phone was
 * held when the effect first appeared*.
 *
 * Signs are normalised so the result reads the way you'd describe the movement:
 * `x` grows as the **right edge lifts**, `y` grows as the **top edge lifts**.
 * Android's raw roll/pitch are the other way round on both axes, which is easy
 * to get backwards downstream.
 *
 * The baseline matters: `SensorManager.getOrientation` reports pitch near
 * -90° for a phone held upright, so using the raw value would leave anything
 * driven by it permanently pinned to one extreme. Zeroing on the first reading
 * makes whatever pose the user is already in the neutral one.
 *
 * Returns a constant zero when the device has no rotation sensor.
 */
@Composable
internal fun rememberDeviceTilt(
    enabled: Boolean = true,
    samplingPeriodMicros: Int = 20_000,
    smoothing: Float = 0.15f
): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, samplingPeriodMicros) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var baseRoll = Float.NaN
        var basePitch = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Some devices report 5 values; the matrix helper wants at most 4.
                val values = if (event.values.size > 4) event.values.copyOf(4) else event.values
                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                val pitch = orientation[1]
                val roll = orientation[2]

                if (baseRoll.isNaN()) {
                    baseRoll = roll
                    basePitch = pitch
                }

                // Negated: positive roll is the *left* edge lifting, and positive
                // pitch is the top edge tipping away.
                val targetX = -(roll - baseRoll)
                val targetY = -(pitch - basePitch)
                val previous = tilt.value
                tilt.value = Offset(
                    previous.x + (targetX - previous.x) * smoothing,
                    previous.y + (targetY - previous.y) * smoothing
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, samplingPeriodMicros)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return tilt
}

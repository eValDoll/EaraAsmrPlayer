package com.asmr.player.ui.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sign
import kotlin.math.sqrt

private const val COVER_MOTION_DEAD_ZONE_DEGREES = 3f
private const val COVER_MOTION_FULL_TRAVEL_DEGREES = 8f
private const val COVER_MOTION_LOW_PASS_FACTOR = 0.14f
private const val MIN_VECTOR_MAGNITUDE = 0.001f

internal data class CoverMotionState(
    val horizontalBias: Float = 0f,
    val verticalBias: Float = 0f
) {
    companion object {
        val Center = CoverMotionState()
    }
}

internal data class CoverMotionAngles(
    val horizontalDegrees: Float,
    val verticalDegrees: Float
)

internal data class ScreenTiltDegrees(
    val horizontal: Float,
    val vertical: Float
)

internal data class ScreenOrientationBasis(
    val rightWorld: FloatArray,
    val upWorld: FloatArray,
    val forwardWorld: FloatArray
)

@Composable
internal fun rememberCoverMotionState(
    enabled: Boolean,
    resetKey: Any? = Unit
): CoverMotionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val rotationVectorSensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    val gravitySensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }
    val selectedSensor = rotationVectorSensor ?: gravitySensor
    val motionState: MutableState<CoverMotionState> = remember {
        mutableStateOf(CoverMotionState.Center)
    }

    DisposableEffect(enabled, sensorManager, selectedSensor, lifecycleOwner, displayRotation, resetKey) {
        if (!enabled || sensorManager == null || selectedSensor == null) {
            motionState.value = CoverMotionState.Center
            return@DisposableEffect onDispose { motionState.value = CoverMotionState.Center }
        }

        var registered = false
        var referenceBasis: ScreenOrientationBasis? = null
        var referenceTilt: ScreenTiltDegrees? = null
        var previousAngles: CoverMotionAngles? = null
        var smoothedState = CoverMotionState.Center
        lateinit var listener: SensorEventListener

        fun reset() {
            referenceBasis = null
            referenceTilt = null
            previousAngles = null
            smoothedState = CoverMotionState.Center
            motionState.value = CoverMotionState.Center
        }

        fun register() {
            if (registered) return
            registered = sensorManager.registerListener(
                listener,
                selectedSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (!registered) reset()
        }

        fun unregister() {
            if (!registered) return
            sensorManager.unregisterListener(listener)
            registered = false
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                val targetState = when (selectedSensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val basis = screenOrientationBasisFromRotationVector(values, displayRotation)
                        if (basis == null) {
                            previousAngles = null
                            smoothedState = CoverMotionState.Center
                            motionState.value = CoverMotionState.Center
                            return
                        }
                        val reference = referenceBasis
                        if (reference == null) {
                            referenceBasis = basis
                            previousAngles = CoverMotionAngles(0f, 0f)
                            smoothedState = CoverMotionState.Center
                            motionState.value = CoverMotionState.Center
                            return
                        }
                        val rawAngles = relativeAnglesFromReference(current = basis, reference = reference)
                        val continuousAngles = previousAngles
                            ?.let { unwrapAngles(previous = it, current = rawAngles) }
                            ?: rawAngles
                        previousAngles = continuousAngles
                        coverMotionStateFromAngles(continuousAngles)
                    }

                    Sensor.TYPE_GRAVITY -> {
                        val tilt = screenTiltDegreesFromGravity(values, displayRotation)
                        if (tilt == null) {
                            smoothedState = CoverMotionState.Center
                            motionState.value = CoverMotionState.Center
                            return
                        }
                        val reference = referenceTilt
                        if (reference == null) {
                            referenceTilt = tilt
                            smoothedState = CoverMotionState.Center
                            motionState.value = CoverMotionState.Center
                            return
                        }
                        coverMotionStateFromAngles(
                            CoverMotionAngles(
                                horizontalDegrees = tilt.horizontal - reference.horizontal,
                                verticalDegrees = tilt.vertical - reference.vertical
                            )
                        )
                    }

                    else -> return
                }

                smoothedState = lowPassCoverMotionState(
                    previous = smoothedState,
                    target = targetState,
                    factor = COVER_MOTION_LOW_PASS_FACTOR
                )
                motionState.value = smoothedState
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    reset()
                    register()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    unregister()
                    reset()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
            reset()
        }
    }

    return motionState.value
}

internal fun CoverMotionState.toAlignment(): Alignment {
    return BiasAlignment(horizontalBias = horizontalBias, verticalBias = verticalBias)
}

internal fun screenOrientationBasisFromRotationVector(
    rotationVector: FloatArray,
    displayRotation: Int
): ScreenOrientationBasis? {
    if (rotationVector.isEmpty() || rotationVector.any { !it.isFinite() }) return null
    val rotationMatrix = FloatArray(9)
    runCatching {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
    }.getOrNull() ?: return null
    return screenOrientationBasisFromRotationMatrix(rotationMatrix, displayRotation)
}

internal fun screenOrientationBasisFromRotationMatrix(
    rotationMatrix: FloatArray,
    displayRotation: Int
): ScreenOrientationBasis? {
    if (rotationMatrix.size < 9) return null
    return ScreenOrientationBasis(
        rightWorld = normalizeVector(
            transformVector(
                rotationMatrix = rotationMatrix,
                vector = screenRightAxisInDeviceCoordinates(displayRotation)
            )
        ) ?: return null,
        upWorld = normalizeVector(
            transformVector(
                rotationMatrix = rotationMatrix,
                vector = screenUpAxisInDeviceCoordinates(displayRotation)
            )
        ) ?: return null,
        forwardWorld = normalizeVector(
            transformVector(
                rotationMatrix = rotationMatrix,
                vector = floatArrayOf(0f, 0f, 1f)
            )
        ) ?: return null
    )
}

internal fun relativeAnglesFromReference(
    current: ScreenOrientationBasis,
    reference: ScreenOrientationBasis
): CoverMotionAngles {
    val rotationVector = relativeRotationVectorWorld(current = current, reference = reference)
        ?: return CoverMotionAngles(horizontalDegrees = 0f, verticalDegrees = 0f)
    val horizontal = Math.toDegrees(dot(rotationVector, reference.upWorld).toDouble()).toFloat()
    val vertical = Math.toDegrees((-dot(rotationVector, reference.rightWorld)).toDouble()).toFloat()
    return CoverMotionAngles(horizontalDegrees = horizontal, verticalDegrees = vertical)
}

internal fun screenTiltDegreesFromGravity(
    gravityDevice: FloatArray,
    displayRotation: Int
): ScreenTiltDegrees? {
    if (gravityDevice.size < 3 || gravityDevice.any { !it.isFinite() }) return null
    val gravityScreen = remapGravityToScreenCoordinates(gravityDevice, displayRotation)
    val magnitude = vectorMagnitude(gravityScreen)
    if (!magnitude.isFinite() || magnitude < MIN_VECTOR_MAGNITUDE) return null

    val normalizedX = (gravityScreen[0] / magnitude).coerceIn(-1f, 1f)
    val normalizedZ = (gravityScreen[2] / magnitude).coerceIn(-1f, 1f)
    if (!normalizedX.isFinite() || !normalizedZ.isFinite()) return null
    return ScreenTiltDegrees(
        horizontal = Math.toDegrees(asin(normalizedX.toDouble())).toFloat(),
        vertical = Math.toDegrees(asin((-normalizedZ).toDouble())).toFloat()
    )
}

internal fun remapGravityToScreenCoordinates(
    gravityDevice: FloatArray,
    displayRotation: Int
): FloatArray {
    val x = gravityDevice[0]
    val y = gravityDevice[1]
    val z = gravityDevice[2]
    return when (displayRotation) {
        Surface.ROTATION_0 -> floatArrayOf(x, y, z)
        Surface.ROTATION_90 -> floatArrayOf(-y, x, z)
        Surface.ROTATION_180 -> floatArrayOf(-x, -y, z)
        Surface.ROTATION_270 -> floatArrayOf(y, -x, z)
        else -> floatArrayOf(x, y, z)
    }
}

internal fun coverMotionStateFromAngles(angles: CoverMotionAngles): CoverMotionState {
    return CoverMotionState(
        horizontalBias = mapDegreesToBias(angles.horizontalDegrees),
        verticalBias = mapDegreesToBias(angles.verticalDegrees)
    )
}

internal fun mapDegreesToBias(
    degrees: Float,
    deadZoneDegrees: Float = COVER_MOTION_DEAD_ZONE_DEGREES,
    fullTravelDegrees: Float = COVER_MOTION_FULL_TRAVEL_DEGREES
): Float {
    if (!degrees.isFinite()) return 0f
    val safeFullTravel = maxOf(fullTravelDegrees, deadZoneDegrees + 0.001f)
    val magnitude = abs(degrees)
    if (magnitude <= deadZoneDegrees) return 0f
    val normalized = ((magnitude - deadZoneDegrees) / (safeFullTravel - deadZoneDegrees))
        .coerceIn(0f, 1f)
    return normalized * sign(degrees)
}

internal fun lowPassCoverMotionState(
    previous: CoverMotionState,
    target: CoverMotionState,
    factor: Float
): CoverMotionState {
    val clampedFactor = factor.coerceIn(0f, 1f)
    return CoverMotionState(
        horizontalBias = lerpBias(previous.horizontalBias, target.horizontalBias, clampedFactor),
        verticalBias = lerpBias(previous.verticalBias, target.verticalBias, clampedFactor)
    )
}

internal fun unwrapAngles(
    previous: CoverMotionAngles,
    current: CoverMotionAngles
): CoverMotionAngles {
    return CoverMotionAngles(
        horizontalDegrees = unwrapAngleDegrees(previous.horizontalDegrees, current.horizontalDegrees),
        verticalDegrees = unwrapAngleDegrees(previous.verticalDegrees, current.verticalDegrees)
    )
}

internal fun unwrapAngleDegrees(previousDegrees: Float, currentDegrees: Float): Float {
    val previousWrapped = wrapDegrees(previousDegrees)
    var delta = currentDegrees - previousWrapped
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return previousDegrees + delta
}

internal fun wrapDegrees(degrees: Float): Float {
    var wrapped = degrees % 360f
    if (wrapped <= -180f) wrapped += 360f
    if (wrapped > 180f) wrapped -= 360f
    return wrapped
}

private fun screenRightAxisInDeviceCoordinates(displayRotation: Int): FloatArray {
    return when (displayRotation) {
        Surface.ROTATION_0 -> floatArrayOf(1f, 0f, 0f)
        Surface.ROTATION_90 -> floatArrayOf(0f, -1f, 0f)
        Surface.ROTATION_180 -> floatArrayOf(-1f, 0f, 0f)
        Surface.ROTATION_270 -> floatArrayOf(0f, 1f, 0f)
        else -> floatArrayOf(1f, 0f, 0f)
    }
}

private fun screenUpAxisInDeviceCoordinates(displayRotation: Int): FloatArray {
    return when (displayRotation) {
        Surface.ROTATION_0 -> floatArrayOf(0f, 1f, 0f)
        Surface.ROTATION_90 -> floatArrayOf(1f, 0f, 0f)
        Surface.ROTATION_180 -> floatArrayOf(0f, -1f, 0f)
        Surface.ROTATION_270 -> floatArrayOf(-1f, 0f, 0f)
        else -> floatArrayOf(0f, 1f, 0f)
    }
}

private fun transformVector(rotationMatrix: FloatArray, vector: FloatArray): FloatArray {
    return floatArrayOf(
        rotationMatrix[0] * vector[0] + rotationMatrix[1] * vector[1] + rotationMatrix[2] * vector[2],
        rotationMatrix[3] * vector[0] + rotationMatrix[4] * vector[1] + rotationMatrix[5] * vector[2],
        rotationMatrix[6] * vector[0] + rotationMatrix[7] * vector[1] + rotationMatrix[8] * vector[2]
    )
}

private fun relativeRotationVectorWorld(
    current: ScreenOrientationBasis,
    reference: ScreenOrientationBasis
): FloatArray? {
    val relativeMatrix = multiplyMatrices(
        left = basisToMatrix(current),
        right = transposeMatrix(basisToMatrix(reference))
    )
    return rotationVectorFromMatrix(relativeMatrix)
}

private fun basisToMatrix(basis: ScreenOrientationBasis): FloatArray {
    return floatArrayOf(
        basis.rightWorld[0], basis.upWorld[0], basis.forwardWorld[0],
        basis.rightWorld[1], basis.upWorld[1], basis.forwardWorld[1],
        basis.rightWorld[2], basis.upWorld[2], basis.forwardWorld[2]
    )
}

private fun transposeMatrix(matrix: FloatArray): FloatArray {
    return floatArrayOf(
        matrix[0], matrix[3], matrix[6],
        matrix[1], matrix[4], matrix[7],
        matrix[2], matrix[5], matrix[8]
    )
}

private fun multiplyMatrices(left: FloatArray, right: FloatArray): FloatArray {
    return floatArrayOf(
        left[0] * right[0] + left[1] * right[3] + left[2] * right[6],
        left[0] * right[1] + left[1] * right[4] + left[2] * right[7],
        left[0] * right[2] + left[1] * right[5] + left[2] * right[8],
        left[3] * right[0] + left[4] * right[3] + left[5] * right[6],
        left[3] * right[1] + left[4] * right[4] + left[5] * right[7],
        left[3] * right[2] + left[4] * right[5] + left[5] * right[8],
        left[6] * right[0] + left[7] * right[3] + left[8] * right[6],
        left[6] * right[1] + left[7] * right[4] + left[8] * right[7],
        left[6] * right[2] + left[7] * right[5] + left[8] * right[8]
    )
}

private fun rotationVectorFromMatrix(matrix: FloatArray): FloatArray? {
    val trace = (matrix[0] + matrix[4] + matrix[8]).coerceIn(-1f, 3f)
    val cosTheta = ((trace - 1f) / 2f).coerceIn(-1f, 1f)
    val theta = kotlin.math.acos(cosTheta)
    if (!theta.isFinite() || theta < 1e-4f) {
        return floatArrayOf(0f, 0f, 0f)
    }

    val axisRaw = floatArrayOf(
        matrix[7] - matrix[5],
        matrix[2] - matrix[6],
        matrix[3] - matrix[1]
    )
    val axis = normalizeVector(axisRaw)
    if (axis != null) {
        return floatArrayOf(
            axis[0] * theta,
            axis[1] * theta,
            axis[2] * theta
        )
    }

    val x = sqrt(maxOf(0f, (matrix[0] + 1f) * 0.5f))
    val y = sqrt(maxOf(0f, (matrix[4] + 1f) * 0.5f))
    val z = sqrt(maxOf(0f, (matrix[8] + 1f) * 0.5f))
    val fallbackAxis = normalizeVector(
        floatArrayOf(
            x * signOrOne(matrix[7] - matrix[5]),
            y * signOrOne(matrix[2] - matrix[6]),
            z * signOrOne(matrix[3] - matrix[1])
        )
    ) ?: return null
    return floatArrayOf(
        fallbackAxis[0] * theta,
        fallbackAxis[1] * theta,
        fallbackAxis[2] * theta
    )
}

private fun normalizeVector(vector: FloatArray): FloatArray? {
    val magnitude = vectorMagnitude(vector)
    if (!magnitude.isFinite() || magnitude < MIN_VECTOR_MAGNITUDE) return null
    return floatArrayOf(
        vector[0] / magnitude,
        vector[1] / magnitude,
        vector[2] / magnitude
    )
}

private fun vectorMagnitude(vector: FloatArray): Float {
    return sqrt(
        vector[0] * vector[0] +
            vector[1] * vector[1] +
            vector[2] * vector[2]
    )
}

private fun dot(a: FloatArray, b: FloatArray): Float {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

private fun signOrOne(value: Float): Float {
    return if (value < 0f) -1f else 1f
}

private fun lerpBias(previous: Float, target: Float, factor: Float): Float {
    return (previous + ((target - previous) * factor)).coerceIn(-1f, 1f)
}

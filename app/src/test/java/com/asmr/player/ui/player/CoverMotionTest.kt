package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CoverMotionTest {

    @Test
    fun zeroAnglesMapToCenteredBias() {
        val state = coverMotionStateFromAngles(CoverMotionAngles(0f, 0f))

        assertEquals(0f, state.horizontalBias, 0.0001f)
        assertEquals(0f, state.verticalBias, 0.0001f)
    }

    @Test
    fun yawMapsToHorizontalBias() {
        val angles = relativeAnglesFromReference(
            current = rotateBasisY(REFERENCE_BASIS, 12f),
            reference = REFERENCE_BASIS
        )
        val state = coverMotionStateFromAngles(angles)

        assertEquals(12f, angles.horizontalDegrees, 0.25f)
        assertEquals(0f, angles.verticalDegrees, 0.25f)
        assertEquals(1f, state.horizontalBias, 0.001f)
    }

    @Test
    fun pitchMapsToVerticalBias() {
        val angles = relativeAnglesFromReference(
            current = rotateBasisX(REFERENCE_BASIS, 12f),
            reference = REFERENCE_BASIS
        )
        val state = coverMotionStateFromAngles(angles)

        assertEquals(0f, angles.horizontalDegrees, 0.25f)
        assertEquals(-12f, angles.verticalDegrees, 0.25f)
        assertEquals(-1f, state.verticalBias, 0.001f)
    }

    @Test
    fun yawAndPitchCombineIntoDiagonalBias() {
        val angles = relativeAnglesFromReference(
            current = rotateBasisY(rotateBasisX(REFERENCE_BASIS, 12f), 12f),
            reference = REFERENCE_BASIS
        )
        val state = coverMotionStateFromAngles(angles)

        assertEquals(12f, angles.horizontalDegrees, 0.5f)
        assertEquals(-12f, angles.verticalDegrees, 0.5f)
        assertEquals(1f, state.horizontalBias, 0.02f)
        assertEquals(-1f, state.verticalBias, 0.02f)
    }

    @Test
    fun unwrapAcrossPlusMinus179StaysContinuous() {
        assertEquals(181f, unwrapAngleDegrees(179f, -179f), 0.0001f)
        assertEquals(-181f, unwrapAngleDegrees(-179f, 179f), 0.0001f)
    }

    @Test
    fun valuesInsideDeadZoneStayCentered() {
        assertEquals(0f, mapDegreesToBias(2.9f), 0.0001f)
        assertEquals(0f, mapDegreesToBias(-2.9f), 0.0001f)
    }

    @Test
    fun degreesPastFullTravelClampToUnitBias() {
        assertEquals(1f, mapDegreesToBias(24f), 0.0001f)
        assertEquals(-1f, mapDegreesToBias(-24f), 0.0001f)
    }

    @Test
    fun gravityFallbackStillUsesTiltMapping() {
        val portrait = screenTiltDegreesFromGravity(
            gravityDevice = floatArrayOf(sinDeg(12f), -cosDeg(12f), 0f),
            displayRotation = android.view.Surface.ROTATION_0
        )!!
        val state = coverMotionStateFromAngles(
            CoverMotionAngles(
                horizontalDegrees = portrait.horizontal,
                verticalDegrees = portrait.vertical
            )
        )

        assertEquals(12f, portrait.horizontal, 0.25f)
        assertEquals(0f, portrait.vertical, 0.25f)
        assertEquals(1f, state.horizontalBias, 0.001f)
    }

    @Test
    fun gravityFallbackRemapsLandscapeTiltToSameScreenDirection() {
        val portrait = screenTiltDegreesFromGravity(
            gravityDevice = floatArrayOf(sinDeg(12f), -cosDeg(12f), 0f),
            displayRotation = android.view.Surface.ROTATION_0
        )!!
        val landscape = screenTiltDegreesFromGravity(
            gravityDevice = floatArrayOf(-cosDeg(12f), -sinDeg(12f), 0f),
            displayRotation = android.view.Surface.ROTATION_90
        )!!

        assertEquals(portrait.horizontal, landscape.horizontal, 0.25f)
        assertEquals(portrait.vertical, landscape.vertical, 0.25f)
    }

    @Test
    fun flatReferenceTiltRightThenReturnFlatReCenters() {
        val flatReference = ScreenOrientationBasis(
            rightWorld = floatArrayOf(1f, 0f, 0f),
            upWorld = floatArrayOf(0f, 1f, 0f),
            forwardWorld = floatArrayOf(0f, 0f, 1f)
        )
        val tilted = rotateBasisY(flatReference, 12f)

        val tiltedAngles = relativeAnglesFromReference(
            current = tilted,
            reference = flatReference
        )
        val returnedAngles = relativeAnglesFromReference(
            current = flatReference,
            reference = flatReference
        )

        assertEquals(12f, tiltedAngles.horizontalDegrees, 0.25f)
        assertEquals(0f, returnedAngles.horizontalDegrees, 0.0001f)
        assertEquals(0f, returnedAngles.verticalDegrees, 0.0001f)
    }

    @Test
    fun invalidSensorInputsAreRejectedByPureHelpers() {
        val invalidRotationVector = screenOrientationBasisFromRotationVector(
            rotationVector = floatArrayOf(Float.NaN, 0f, 0f, 1f),
            displayRotation = android.view.Surface.ROTATION_0
        )
        val invalidGravity = screenTiltDegreesFromGravity(
            gravityDevice = floatArrayOf(Float.NaN, 0f, 0f),
            displayRotation = android.view.Surface.ROTATION_0
        )

        assertNull(invalidRotationVector)
        assertNull(invalidGravity)
    }

    private fun rotateBasisX(basis: ScreenOrientationBasis, degrees: Float): ScreenOrientationBasis {
        return rotateBasis(
            basis = basis,
            matrix = floatArrayOf(
                1f, 0f, 0f,
                0f, cosDeg(degrees), -sinDeg(degrees),
                0f, sinDeg(degrees), cosDeg(degrees)
            )
        )
    }

    private fun rotateBasisY(basis: ScreenOrientationBasis, degrees: Float): ScreenOrientationBasis {
        return rotateBasis(
            basis = basis,
            matrix = floatArrayOf(
                cosDeg(degrees), 0f, sinDeg(degrees),
                0f, 1f, 0f,
                -sinDeg(degrees), 0f, cosDeg(degrees)
            )
        )
    }

    private fun rotateBasis(basis: ScreenOrientationBasis, matrix: FloatArray): ScreenOrientationBasis {
        return ScreenOrientationBasis(
            rightWorld = normalize(transform(matrix, basis.rightWorld)),
            upWorld = normalize(transform(matrix, basis.upWorld)),
            forwardWorld = normalize(transform(matrix, basis.forwardWorld))
        )
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = kotlin.math.sqrt(
            vector[0] * vector[0] +
                vector[1] * vector[1] +
                vector[2] * vector[2]
        )
        return floatArrayOf(
            vector[0] / magnitude,
            vector[1] / magnitude,
            vector[2] / magnitude
        )
    }

    private fun transform(matrix: FloatArray, vector: FloatArray): FloatArray {
        return floatArrayOf(
            matrix[0] * vector[0] + matrix[1] * vector[1] + matrix[2] * vector[2],
            matrix[3] * vector[0] + matrix[4] * vector[1] + matrix[5] * vector[2],
            matrix[6] * vector[0] + matrix[7] * vector[1] + matrix[8] * vector[2]
        )
    }

    private fun sinDeg(degrees: Float): Float = sin(Math.toRadians(degrees.toDouble())).toFloat()

    private fun cosDeg(degrees: Float): Float = cos(Math.toRadians(degrees.toDouble())).toFloat()

    private companion object {
        val REFERENCE_BASIS = ScreenOrientationBasis(
            rightWorld = floatArrayOf(1f, 0f, 0f),
            upWorld = floatArrayOf(0f, 1f, 0f),
            forwardWorld = floatArrayOf(0f, 0f, 1f)
        )
    }
}

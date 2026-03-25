package com.example.gaitguardian.pipeline.prediction.rtmo

import android.util.Log

class RtmoTemporalInterpolator(
    private val filterEnabled: Boolean = false,
    private val debugLoggingEnabled: Boolean = true
) {
    companion object {
        private const val TAG = "RtmoTemporalInterp"
        private const val dimensionsPerJoint = 2
    }

    fun interpolate(input: RtmoPhaseModelInput): RtmoInterpolatedSequence {
        val coordinates = Array(input.sequenceLength) {
            Array(input.keypointIndices.size) {
                FloatArray(dimensionsPerJoint) { Float.NaN }
            }
        }
        val originalCoordinates = Array(input.sequenceLength) { frameIndex ->
            Array(input.keypointIndices.size) { jointIndex ->
                coordinates[frameIndex][jointIndex].copyOf()
            }
        }

        input.frames.forEachIndexed { frameIndex, frame ->
            if (!frame.hasPose) {
                return@forEachIndexed
            }

            input.keypointIndices.indices.forEach { jointIndex ->
                val base = jointIndex * RtmoPredictionConfig.valuesPerKeypoint
                coordinates[frameIndex][jointIndex][0] = frame.features.getOrElse(base) { Float.NaN }
                coordinates[frameIndex][jointIndex][1] = frame.features.getOrElse(base + 1) { Float.NaN }
            }
        }
        coordinates.indices.forEach { frameIndex ->
            input.keypointIndices.indices.forEach { jointIndex ->
                originalCoordinates[frameIndex][jointIndex][0] = coordinates[frameIndex][jointIndex][0]
                originalCoordinates[frameIndex][jointIndex][1] = coordinates[frameIndex][jointIndex][1]
            }
        }

        val nanFrameCount = coordinates.count { frame ->
            frame.any { joint -> joint[0].isNaN() }
        }

        input.keypointIndices.indices.forEach { jointIndex ->
            interpolateJointDimension(coordinates, jointIndex, 0)
            interpolateJointDimension(coordinates, jointIndex, 1)
        }

        if (filterEnabled) {
            Log.w(TAG, "Low-pass filtering is not enabled in Android yet; using interpolated coordinates only.")
        }
        if (debugLoggingEnabled) {
            logInterpolationSummary(input, originalCoordinates, coordinates)
        }

        return RtmoInterpolatedSequence(
            fps = input.fps,
            frameCount = input.sequenceLength,
            jointCount = input.keypointIndices.size,
            dimensionsPerJoint = dimensionsPerJoint,
            nanFrameCount = nanFrameCount,
            coordinates = coordinates
        )
    }

    private fun interpolateJointDimension(
        coordinates: Array<Array<FloatArray>>,
        jointIndex: Int,
        dimensionIndex: Int
    ) {
        val values = FloatArray(coordinates.size) { frameIndex ->
            coordinates[frameIndex][jointIndex][dimensionIndex]
        }

        val validIndices = values.indices.filter { !values[it].isNaN() }
        if (validIndices.isEmpty()) {
            return
        }

        val firstValidIndex = validIndices.first()
        val lastValidIndex = validIndices.last()

        for (index in 0 until firstValidIndex) {
            values[index] = values[firstValidIndex]
        }
        for (index in lastValidIndex + 1 until values.size) {
            values[index] = values[lastValidIndex]
        }

        var validPointer = 0
        while (validPointer < validIndices.size - 1) {
            val leftIndex = validIndices[validPointer]
            val rightIndex = validIndices[validPointer + 1]
            val leftValue = values[leftIndex]
            val rightValue = values[rightIndex]

            if (rightIndex - leftIndex > 1) {
                for (index in leftIndex + 1 until rightIndex) {
                    val ratio = (index - leftIndex).toFloat() / (rightIndex - leftIndex).toFloat()
                    values[index] = leftValue + ratio * (rightValue - leftValue)
                }
            }
            validPointer += 1
        }

        values.indices.forEach { frameIndex ->
            coordinates[frameIndex][jointIndex][dimensionIndex] = values[frameIndex]
        }
    }

    private fun logInterpolationSummary(
        input: RtmoPhaseModelInput,
        originalCoordinates: Array<Array<FloatArray>>,
        interpolatedCoordinates: Array<Array<FloatArray>>
    ) {
        input.keypointIndices.indices.forEach { jointIndex ->
            val beforeNanCount = originalCoordinates.count { frame -> frame[jointIndex][0].isNaN() || frame[jointIndex][1].isNaN() }
            val afterNanCount = interpolatedCoordinates.count { frame -> frame[jointIndex][0].isNaN() || frame[jointIndex][1].isNaN() }
            if (beforeNanCount == 0 && afterNanCount == 0) {
                return@forEach
            }

            Log.e(
                TAG,
                "joint=${input.keypointIndices[jointIndex]} beforeNanFrames=$beforeNanCount afterNanFrames=$afterNanCount"
            )

            val sampleFrames = originalCoordinates.indices
                .filter { frameIndex ->
                    originalCoordinates[frameIndex][jointIndex][0].isNaN() ||
                        originalCoordinates[frameIndex][jointIndex][1].isNaN()
                }
                .take(3)

            sampleFrames.forEach { frameIndex ->
                val before = originalCoordinates[frameIndex][jointIndex]
                val after = interpolatedCoordinates[frameIndex][jointIndex]
                Log.e(
                    TAG,
                    "joint=${input.keypointIndices[jointIndex]} frame=$frameIndex " +
                        "before=(x=${formatDebugValue(before[0])}, y=${formatDebugValue(before[1])}) " +
                        "after=(x=${formatDebugValue(after[0])}, y=${formatDebugValue(after[1])})"
                )
            }
        }
    }

    private fun formatDebugValue(value: Float): String {
        return if (value.isNaN()) "NaN" else "%.3f".format(value)
    }
}

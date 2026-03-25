package com.example.gaitguardian.pipeline.prediction.rtmo

import kotlin.math.sqrt

class RtmoSequenceNormalizer(
    private val config: RtmoPredictionConfig = RtmoPredictionConfig
) {
    companion object {
        private const val dimensionsPerJoint = 2
    }

    fun normalize(sequence: RtmoInterpolatedSequence): RtmoNormalizedSequence {
        val normalizationScale = computeSequenceScale(sequence.coordinates)
        val normalizedCoordinates = Array(sequence.frameCount) { frameIndex ->
            Array(sequence.jointCount) { jointIndex ->
                floatArrayOf(
                    sequence.coordinates[frameIndex][jointIndex][0] / normalizationScale,
                    1f - (sequence.coordinates[frameIndex][jointIndex][1] / normalizationScale)
                )
            }
        }

        return RtmoNormalizedSequence(
            fps = sequence.fps,
            frameCount = sequence.frameCount,
            jointCount = sequence.jointCount,
            dimensionsPerJoint = sequence.dimensionsPerJoint,
            normalizationScale = normalizationScale,
            coordinates = normalizedCoordinates
        )
    }

    private fun computeSequenceScale(coordinates: Array<Array<FloatArray>>): Float {
        var maxDistance = 0f

        coordinates.forEach { frame ->
            val leftShoulder = reducedKeypoint(frame, 1)
            val rightShoulder = reducedKeypoint(frame, 2)
            val leftAnkle = reducedKeypoint(frame, 11)
            val rightAnkle = reducedKeypoint(frame, 12)

            if (leftShoulder != null && rightShoulder != null && leftAnkle != null && rightAnkle != null) {
                val midShoulder = midpoint(leftShoulder, rightShoulder)
                val midAnkle = midpoint(leftAnkle, rightAnkle)
                maxDistance = maxOf(maxDistance, distance(midShoulder, midAnkle))
            }
        }

        return maxOf(maxDistance, 1f)
    }

    private fun reducedKeypoint(frame: Array<FloatArray>, reducedIndex: Int): Pair<Float, Float>? {
        config.landmarkIndices.getOrNull(reducedIndex) ?: return null
        if (reducedIndex !in frame.indices) {
            return null
        }
        val point = frame[reducedIndex]
        return if (point[0].isNaN() || point[1].isNaN()) {
            null
        } else {
            Pair(point[0], point[1])
        }
    }

    private fun midpoint(first: Pair<Float, Float>, second: Pair<Float, Float>): Pair<Float, Float> {
        return Pair(
            (first.first + second.first) / 2f,
            (first.second + second.second) / 2f
        )
    }

    private fun distance(first: Pair<Float, Float>, second: Pair<Float, Float>): Float {
        val dx = first.first - second.first
        val dy = first.second - second.second
        return sqrt(dx * dx + dy * dy)
    }
}

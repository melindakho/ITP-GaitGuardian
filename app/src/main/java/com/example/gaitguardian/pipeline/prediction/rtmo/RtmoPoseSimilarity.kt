package com.example.gaitguardian.pipeline.prediction.rtmo

import com.example.gaitguardian.pipeline.pose.core.PosePerson
import kotlin.math.sqrt

class RtmoPoseSimilarity(
    private val config: RtmoTrackingConfig = RtmoTrackingConfig
) {
    fun similarity(first: PosePerson, second: PosePerson): Float {
        val visiblePairs = mutableListOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()

        val jointCount = minOf(first.keypointCount, second.keypointCount)
        for (jointIndex in 0 until jointCount) {
            val firstBase = jointIndex * first.valuesPerKeypoint
            val secondBase = jointIndex * second.valuesPerKeypoint
            val firstConfidence = first.keypoints.getOrElse(firstBase + 2) { 0f }
            val secondConfidence = second.keypoints.getOrElse(secondBase + 2) { 0f }
            if (firstConfidence <= config.keypointVisibilityThreshold ||
                secondConfidence <= config.keypointVisibilityThreshold
            ) {
                continue
            }

            val firstPoint = Pair(
                first.keypoints.getOrElse(firstBase) { 0f },
                first.keypoints.getOrElse(firstBase + 1) { 0f }
            )
            val secondPoint = Pair(
                second.keypoints.getOrElse(secondBase) { 0f },
                second.keypoints.getOrElse(secondBase + 1) { 0f }
            )
            visiblePairs += firstPoint to secondPoint
        }

        if (visiblePairs.size < config.minPoseJoints) {
            return 0f
        }

        val firstPoints = visiblePairs.map { it.first }
        val secondPoints = visiblePairs.map { it.second }

        val firstCenter = centroid(firstPoints)
        val secondCenter = centroid(secondPoints)
        val centerDistance = distance(firstCenter, secondCenter)

        val firstBodySize = bodySize(firstPoints)
        val secondBodySize = bodySize(secondPoints)
        val averageBodySize = ((firstBodySize + secondBodySize) / 2f).coerceAtLeast(1e-6f)
        val normalizedDistance = centerDistance / averageBodySize

        val disparity = procrustesDisparity(firstPoints, secondPoints)
        return 1f - disparity - normalizedDistance * 0.1f
    }

    private fun centroid(points: List<Pair<Float, Float>>): Pair<Float, Float> {
        var sumX = 0f
        var sumY = 0f
        points.forEach { point ->
            sumX += point.first
            sumY += point.second
        }
        return Pair(sumX / points.size.toFloat(), sumY / points.size.toFloat())
    }

    private fun bodySize(points: List<Pair<Float, Float>>): Float {
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val height = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        val width = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        return sqrt(height * height + width * width).coerceAtLeast(1e-6f)
    }

    private fun procrustesDisparity(
        firstPoints: List<Pair<Float, Float>>,
        secondPoints: List<Pair<Float, Float>>
    ): Float {
        val centeredFirst = centerAndNormalize(firstPoints)
        val centeredSecond = centerAndNormalize(secondPoints)

        val firstMatrix = centeredFirst.first
        val secondMatrix = centeredSecond.first

        val covariance00 = sumProduct(firstMatrix, secondMatrix, 0, 0)
        val covariance01 = sumProduct(firstMatrix, secondMatrix, 0, 1)
        val covariance10 = sumProduct(firstMatrix, secondMatrix, 1, 0)
        val covariance11 = sumProduct(firstMatrix, secondMatrix, 1, 1)

        val rotation = best2dRotation(covariance00, covariance01, covariance10, covariance11)

        var disparity = 0f
        for (index in firstMatrix.indices) {
            val ax = firstMatrix[index].first
            val ay = firstMatrix[index].second
            val bx = secondMatrix[index].first
            val by = secondMatrix[index].second

            val rotatedBx = rotation.first.first * bx + rotation.first.second * by
            val rotatedBy = rotation.second.first * bx + rotation.second.second * by

            val dx = ax - rotatedBx
            val dy = ay - rotatedBy
            disparity += dx * dx + dy * dy
        }
        return disparity
    }

    private fun centerAndNormalize(points: List<Pair<Float, Float>>): Pair<List<Pair<Float, Float>>, Pair<Float, Float>> {
        val center = centroid(points)
        val centered = points.map { Pair(it.first - center.first, it.second - center.second) }
        var squaredSum = 0f
        centered.forEach { point ->
            squaredSum += point.first * point.first + point.second * point.second
        }
        val norm = sqrt(squaredSum).coerceAtLeast(1e-6f)
        return centered.map { Pair(it.first / norm, it.second / norm) } to center
    }

    private fun sumProduct(
        firstMatrix: List<Pair<Float, Float>>,
        secondMatrix: List<Pair<Float, Float>>,
        firstColumn: Int,
        secondColumn: Int
    ): Float {
        var total = 0f
        for (index in firstMatrix.indices) {
            val firstValue = if (firstColumn == 0) firstMatrix[index].first else firstMatrix[index].second
            val secondValue = if (secondColumn == 0) secondMatrix[index].first else secondMatrix[index].second
            total += firstValue * secondValue
        }
        return total
    }

    private fun best2dRotation(a: Float, b: Float, c: Float, d: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val trace = a + d
        val skew = b - c
        val norm = sqrt(trace * trace + skew * skew).coerceAtLeast(1e-6f)
        val cosTheta = trace / norm
        val sinTheta = skew / norm
        return Pair(
            Pair(cosTheta, -sinTheta),
            Pair(sinTheta, cosTheta)
        )
    }

    private fun distance(first: Pair<Float, Float>, second: Pair<Float, Float>): Float {
        val dx = first.first - second.first
        val dy = first.second - second.second
        return sqrt(dx * dx + dy * dy)
    }
}

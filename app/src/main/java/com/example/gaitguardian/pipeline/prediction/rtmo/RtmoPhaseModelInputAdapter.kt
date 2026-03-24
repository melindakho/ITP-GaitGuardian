package com.example.gaitguardian.pipeline.prediction.rtmo

import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence
import kotlin.math.sqrt

class RtmoPhaseModelInputAdapter(
    private val config: RtmoPredictionConfig = RtmoPredictionConfig
) {

    fun adapt(sequence: PoseSequence): RtmoPhaseModelInput {
        require(sequence.backend == PoseBackend.RTMO) {
            "RtmoPhaseModelInputAdapter only supports RTMO sequences"
        }

        val selectedPersons = sequence.frames.map { frame ->
            val selectedPersonIndex = frame.persons.indices.maxByOrNull { personIndex ->
                averageConfidence(frame.persons[personIndex])
            }
            selectedPersonIndex?.let(frame.persons::get)
        }
        val normalizationScale = computeSequenceScale(selectedPersons)

        val frames = sequence.frames.mapIndexed { frameIndex, frame ->
            val selectedPersonIndex = frame.persons.indices.maxByOrNull { personIndex ->
                averageConfidence(frame.persons[personIndex])
            }
            val selectedPerson = selectedPersonIndex?.let(frame.persons::get)
            val features = selectedPerson?.let { buildNormalizedFeatures(it, normalizationScale) }
                ?: FloatArray(config.featuresPerFrame)

            RtmoPhaseFrame(
                frameIndex = frameIndex,
                features = features,
                hasPose = selectedPerson != null,
                sourcePersonIndex = selectedPersonIndex
            )
        }

        return RtmoPhaseModelInput(
            fps = sequence.fps,
            totalFrames = sequence.totalFrames,
            detectedFrames = frames.count { it.hasPose },
            featuresPerFrame = config.featuresPerFrame,
            keypointIndices = config.landmarkIndices.toList(),
            frames = frames
        )
    }

    private fun buildNormalizedFeatures(person: PosePerson, normalizationScale: Float): FloatArray {
        val output = FloatArray(config.featuresPerFrame)
        if (person.keypointCount <= config.landmarkIndices.max()) {
            return output
        }

        config.landmarkIndices.forEachIndexed { outputIndex, keypointIndex ->
            val keypointOffset = keypointIndex * person.valuesPerKeypoint
            val x = person.keypoints.getOrElse(keypointOffset) { 0f }
            val y = person.keypoints.getOrElse(keypointOffset + 1) { 0f }
            val confidence = person.keypoints.getOrElse(keypointOffset + 2) { 0f }

            val outputOffset = outputIndex * config.valuesPerKeypoint
            output[outputOffset] = x / normalizationScale
            output[outputOffset + 1] = y / normalizationScale
            output[outputOffset + 2] = confidence
        }

        return output
    }

    private fun averageConfidence(person: PosePerson): Float {
        if (person.valuesPerKeypoint < 3 || person.keypointCount == 0) {
            return 0f
        }

        var total = 0f
        for (keypointIndex in 0 until person.keypointCount) {
            val confidenceIndex = keypointIndex * person.valuesPerKeypoint + 2
            total += person.keypoints.getOrElse(confidenceIndex) { 0f }
        }
        return total / person.keypointCount.toFloat()
    }

    private fun computeSequenceScale(persons: List<PosePerson?>): Float {
        var maxDistance = 0f

        persons.forEach { person ->
            if (person == null || person.keypointCount <= config.landmarkIndices.max()) {
                return@forEach
            }

            val leftShoulder = reducedKeypoint(person, 1)
            val rightShoulder = reducedKeypoint(person, 2)
            val leftAnkle = reducedKeypoint(person, 11)
            val rightAnkle = reducedKeypoint(person, 12)

            if (leftShoulder != null && rightShoulder != null && leftAnkle != null && rightAnkle != null) {
                val midShoulder = midpoint(leftShoulder, rightShoulder)
                val midAnkle = midpoint(leftAnkle, rightAnkle)
                maxDistance = maxOf(maxDistance, distance(midShoulder, midAnkle))
            }
        }

        return maxOf(maxDistance, 1f)
    }

    private fun reducedKeypoint(person: PosePerson, reducedIndex: Int): Pair<Float, Float>? {
        val keypointIndex = config.landmarkIndices.getOrNull(reducedIndex) ?: return null
        val offset = keypointIndex * person.valuesPerKeypoint
        if (offset + 1 >= person.keypoints.size) {
            return null
        }
        return Pair(person.keypoints[offset], person.keypoints[offset + 1])
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

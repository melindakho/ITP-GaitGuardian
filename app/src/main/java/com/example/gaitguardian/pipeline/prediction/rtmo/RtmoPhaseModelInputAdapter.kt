package com.example.gaitguardian.pipeline.prediction.rtmo

import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence
class RtmoPhaseModelInputAdapter(
    private val config: RtmoPredictionConfig = RtmoPredictionConfig
) {

    fun adapt(sequence: PoseSequence): RtmoPhaseModelInput {
        require(sequence.backend == PoseBackend.RTMO) {
            "RtmoPhaseModelInputAdapter only supports RTMO sequences"
        }

        val frames = sequence.frames.mapIndexed { frameIndex, frame ->
            val selectedPersonIndex = frame.persons.indices.maxByOrNull { personIndex ->
                averageConfidence(frame.persons[personIndex])
            }
            val selectedPerson = selectedPersonIndex?.let(frame.persons::get)
            val features = selectedPerson?.let(::buildReducedFeatures)
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

    private fun buildReducedFeatures(person: PosePerson): FloatArray {
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
            output[outputOffset] = x
            output[outputOffset + 1] = y
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
}

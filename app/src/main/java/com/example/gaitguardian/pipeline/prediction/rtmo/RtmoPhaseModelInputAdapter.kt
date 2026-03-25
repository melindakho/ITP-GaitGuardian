package com.example.gaitguardian.pipeline.prediction.rtmo
import android.util.Log

import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence
import kotlin.math.sqrt

class RtmoPhaseModelInputAdapter(
    private val config: RtmoPredictionConfig = RtmoPredictionConfig
) {
    companion object {
        private const val TAG = "RtmoPhaseInput"
        private const val TEMP_SELECTION_STRATEGY = "dominant_track_frequency"
    }

    fun adapt(sequence: PoseSequence): RtmoPhaseModelInput {
        require(sequence.backend == PoseBackend.RTMO) {
            "RtmoPhaseModelInputAdapter only supports RTMO sequences"
        }

        val trackedFrames = trackSequence(sequence)
        val selectedTrackId = selectDominantTrackId(trackedFrames)
        val selectedTrackFrames = trackedFrames.map { trackedDetections ->
            trackedDetections.firstOrNull { tracked ->
                selectedTrackId != null && tracked.trackId == selectedTrackId
            }
        }
        logTrackingSummary(trackedFrames, selectedTrackId)
        val normalizationScale = computeSequenceScale(selectedTrackFrames.map { it?.pose })
        Log.e(TAG, "normalizationScale=$normalizationScale")

        val frames = sequence.frames.mapIndexed { frameIndex, _ ->
            val trackedPerson = selectedTrackFrames[frameIndex]
            val selectedPerson = trackedPerson?.pose
            val features = selectedPerson?.let { buildNormalizedFeatures(it, normalizationScale) }
                ?: FloatArray(config.featuresPerFrame)

            RtmoPhaseFrame(
                frameIndex = frameIndex,
                features = features,
                hasPose = selectedPerson != null,
                sourcePersonIndex = trackedPerson?.detectionIndex
            )
        }

        return RtmoPhaseModelInput(
            fps = sequence.fps,
            totalFrames = sequence.totalFrames,
            detectedFrames = frames.count { it.hasPose },
            featuresPerFrame = config.featuresPerFrame,
            keypointIndices = config.landmarkIndices.toList(),
            selectedTrackId = selectedTrackId,
            selectionStrategy = TEMP_SELECTION_STRATEGY,
            frames = frames
        )
    }

    private fun trackSequence(sequence: PoseSequence): List<List<RtmoTrackedPerson>> {
        val tracker = RtmoPersonTracker()

        return sequence.frames.map { frame ->
            tracker.update(frame.persons)
        }
    }

    private fun selectDominantTrackId(trackedFrames: List<List<RtmoTrackedPerson>>): Int? {
        val counts = mutableMapOf<Int, Int>()
        trackedFrames.forEach { trackedDetections ->
            trackedDetections.forEach { tracked ->
                counts[tracked.trackId] = (counts[tracked.trackId] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }?.key
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

        persons.forEachIndexed { frameIndex, person ->
            if (person == null || person.keypointCount <= config.landmarkIndices.max()) {
                return@forEachIndexed
            }

            val leftShoulder = reducedKeypoint(person, 1)
            val rightShoulder = reducedKeypoint(person, 2)
            val leftAnkle = reducedKeypoint(person, 11)
            val rightAnkle = reducedKeypoint(person, 12)

            if (leftShoulder != null && rightShoulder != null && leftAnkle != null && rightAnkle != null) {
                val midShoulder = midpoint(leftShoulder, rightShoulder)
                val midAnkle = midpoint(leftAnkle, rightAnkle)
                val distance = distance(midShoulder, midAnkle)

                Log.e(
                    TAG,
                    "frame=$frameIndex midShoulder=(${midShoulder.first}, ${midShoulder.second}) " +
                        "midAnkle=(${midAnkle.first}, ${midAnkle.second}) distance=$distance"
                )
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

    private fun logTrackingSummary(trackedFrames: List<List<RtmoTrackedPerson>>, selectedTrackId: Int?) {
        Log.e(
            TAG,
            "trackSelection strategy=$TEMP_SELECTION_STRATEGY selectedTrackId=$selectedTrackId " +
                "framesWithTracks=${trackedFrames.count { it.isNotEmpty() }}/${
                    trackedFrames.size
                }"
        )

        trackedFrames.forEachIndexed { frameIndex, trackedDetections ->
            if (trackedDetections.isEmpty()) {
                return@forEachIndexed
            }

            val summary = trackedDetections.joinToString(" ; ") { tracked ->
                val bboxScore = "%.3f".format(tracked.pose.bboxScore)
                val marker = if (tracked.trackId == selectedTrackId) "*" else ""
                "track=${tracked.trackId}$marker detIndex=${tracked.detectionIndex} bbox=$bboxScore"
            }
            Log.e(TAG, "frame=$frameIndex tracks=$summary")
        }
    }
}

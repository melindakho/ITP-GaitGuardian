package com.example.gaitguardian.pipeline.prediction.rtmo

import com.example.gaitguardian.pipeline.pose.core.PosePerson

data class RtmoTrackedPerson(
    val trackId: Int,
    val pose: PosePerson,
    val age: Int,
    val detectionIndex: Int
)

private data class RtmoTrack(
    val id: Int,
    var pose: PosePerson,
    var age: Int
)

class RtmoPersonTracker(
    private val config: RtmoTrackingConfig = RtmoTrackingConfig,
    private val similarity: RtmoPoseSimilarity = RtmoPoseSimilarity(config)
) {
    private var nextId = 0
    private val tracks = mutableMapOf<Int, RtmoTrack>()

    fun update(detections: List<PosePerson>): List<RtmoTrackedPerson> {
        val validDetections = detections.filter { detection ->
            detection.bboxScore >= config.bboxThreshold
        }

        tracks.values.forEach { it.age += 1 }

        if (validDetections.isEmpty() && tracks.isEmpty()) {
            return emptyList()
        }

        val trackIds = tracks.keys.toList()
        val scores = Array(validDetections.size) { FloatArray(trackIds.size) }
        validDetections.forEachIndexed { detectionIndex, detection ->
            trackIds.forEachIndexed { trackIndex, trackId ->
                scores[detectionIndex][trackIndex] = similarity.similarity(detection, tracks.getValue(trackId).pose)
            }
        }

        val candidates = mutableListOf<Triple<Float, Int, Int>>()
        validDetections.indices.forEach { detectionIndex ->
            trackIds.indices.forEach { trackIndex ->
                candidates += Triple(scores[detectionIndex][trackIndex], detectionIndex, trackIndex)
            }
        }
        candidates.sortByDescending { it.first }

        val usedDetections = mutableSetOf<Int>()
        val usedTracks = mutableSetOf<Int>()
        val matches = mutableListOf<Pair<Int, Int>>()

        for ((score, detectionIndex, trackIndex) in candidates) {
            if (score < config.poseSimThreshold) {
                break
            }
            if (detectionIndex in usedDetections || trackIndex in usedTracks) {
                continue
            }
            matches += detectionIndex to trackIndex
            usedDetections += detectionIndex
            usedTracks += trackIndex
        }

        matches.forEach { (detectionIndex, trackIndex) ->
            val track = tracks.getValue(trackIds[trackIndex])
            track.pose = validDetections[detectionIndex]
            track.age = 0
        }

        validDetections.indices
            .filterNot { it in usedDetections }
            .forEach { detectionIndex ->
                val trackId = nextId++
                tracks[trackId] = RtmoTrack(
                    id = trackId,
                    pose = validDetections[detectionIndex],
                    age = 0
                )
            }

        return tracks.values
            .filter { it.age == 0 }
            .map { track ->
                RtmoTrackedPerson(
                    trackId = track.id,
                    pose = track.pose,
                    age = track.age,
                    detectionIndex = validDetections.indexOf(track.pose)
                )
            }
    }
}

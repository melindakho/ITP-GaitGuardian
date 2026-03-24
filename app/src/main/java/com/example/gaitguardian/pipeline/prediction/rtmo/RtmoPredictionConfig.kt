package com.example.gaitguardian.pipeline.prediction.rtmo

object RtmoPredictionConfig {
    // COCO-17 joints used by the current RTMO prediction path.
    // This order is the contract for the future LSTM input.
    val landmarkIndices = intArrayOf(
        0,   // nose
        5,   // left_shoulder
        6,   // right_shoulder
        7,   // left_elbow
        8,   // right_elbow
        9,   // left_wrist
        10,  // right_wrist
        11,  // left_hip
        12,  // right_hip
        13,  // left_knee
        14,  // right_knee
        15,  // left_ankle
        16   // right_ankle
    )

    const val valuesPerKeypoint = 3
    val keypointCount = landmarkIndices.size
    val featuresPerFrame = keypointCount * valuesPerKeypoint
}

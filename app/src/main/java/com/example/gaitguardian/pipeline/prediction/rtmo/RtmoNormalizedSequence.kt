package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoNormalizedSequence(
    val fps: Float,
    val frameCount: Int,
    val jointCount: Int,
    val dimensionsPerJoint: Int,
    val normalizationScale: Float,
    val coordinates: Array<Array<FloatArray>>
)

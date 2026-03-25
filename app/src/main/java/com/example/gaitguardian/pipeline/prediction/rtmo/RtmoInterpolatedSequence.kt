package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoInterpolatedSequence(
    val fps: Float,
    val frameCount: Int,
    val jointCount: Int,
    val dimensionsPerJoint: Int,
    val nanFrameCount: Int,
    val coordinates: Array<Array<FloatArray>>
)

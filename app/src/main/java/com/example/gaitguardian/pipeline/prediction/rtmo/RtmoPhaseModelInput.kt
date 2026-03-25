package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoPhaseFrame(
    val frameIndex: Int,
    val features: FloatArray,
    val hasPose: Boolean,
    val sourcePersonIndex: Int?
)

data class RtmoPhaseModelInput(
    val fps: Float,
    val totalFrames: Int,
    val detectedFrames: Int,
    val featuresPerFrame: Int,
    val keypointIndices: List<Int>,
    val selectedTrackId: Int?,
    val selectionStrategy: String,
    val frames: List<RtmoPhaseFrame>
) {
    val sequenceLength: Int
        get() = frames.size
}

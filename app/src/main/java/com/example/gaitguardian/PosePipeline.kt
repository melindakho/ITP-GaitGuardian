package com.example.gaitguardian

import android.net.Uri

enum class PoseBackend {
    RTMO,
    MEDIAPIPE
}

enum class PoseCoordinateSpace {
    PIXEL,
    NORMALIZED
}

data class PosePerson(
    val keypoints: FloatArray,
    val keypointCount: Int,
    val valuesPerKeypoint: Int,
    val coordinateSpace: PoseCoordinateSpace
)

data class PoseFrame(
    val persons: List<PosePerson>
)

data class PoseSequence(
    val backend: PoseBackend,
    val frames: List<PoseFrame>,
    val fps: Float,
    val totalFrames: Int,
    val duration: Long
)

interface PoseExtractor {
    suspend fun initialize(): Boolean

    suspend fun extractPoseSequence(
        videoUri: Uri,
        progressCallback: FrameProgressCallback? = null
    ): PoseSequence?

    fun cleanup()
}

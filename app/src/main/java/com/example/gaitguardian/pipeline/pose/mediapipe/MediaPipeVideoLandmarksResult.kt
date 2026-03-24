package com.example.gaitguardian.pipeline.pose.mediapipe

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

data class MediaPipeVideoLandmarksResult(
    val landmarks: List<List<NormalizedLandmark>>,
    val fps: Float,
    val totalFrames: Int,
    val duration: Long
)

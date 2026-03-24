package com.example.gaitguardian

fun interface FrameProgressCallback {
    fun onProgress(currentFrame: Int, totalFrames: Int, stage: String)
}

package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoPhasePredictionResult(
    val success: Boolean,
    val inputName: String? = null,
    val outputName: String? = null,
    val inputShape: LongArray? = null,
    val outputShape: LongArray? = null,
    val labels: List<String> = emptyList(),
    val frameLabels: List<String> = emptyList(),
    val frameClassIndices: List<Int> = emptyList(),
    val phaseDurationsSec: Map<String, Float> = emptyMap(),
    val orderedPhaseDurationsSec: FloatArray = FloatArray(0),
    val errorMessage: String? = null
)

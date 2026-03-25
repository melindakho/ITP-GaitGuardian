package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoSeverityPredictionResult(
    val success: Boolean,
    val severityLabel: String? = null,
    val predictedClassIndex: Int? = null,
    val classProbabilities: FloatArray = FloatArray(0),
    val featureNames: List<String> = emptyList(),
    val featureVector: FloatArray = FloatArray(0),
    val inputName: String? = null,
    val outputNames: List<String> = emptyList(),
    val inputShape: LongArray? = null,
    val outputShapes: List<LongArray?> = emptyList(),
    val errorMessage: String? = null
)

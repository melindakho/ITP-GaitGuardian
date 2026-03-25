package com.example.gaitguardian.pipeline.prediction.rtmo

data class RtmoSeverityFeatures(
    val featureNames: List<String>,
    val featureVector: FloatArray,
    val sitToStandSec: Float,
    val walkFromChairSec: Float,
    val turningSec: Float,
    val walkToChairSec: Float,
    val standToSitSec: Float,
    val turnWalkRatio: Float
)

package com.example.gaitguardian.pipeline.prediction.rtmo

class RtmoSeverityFeatureBuilder {
    companion object {
        val FEATURE_NAMES = listOf(
            "sit_to_stand",
            "walk_from_chair",
            "turning",
            "walk_to_chair",
            "stand_to_sit",
            "turn_walk_ratio"
        )
    }

    fun build(phasePrediction: RtmoPhasePredictionResult): RtmoSeverityFeatures {
        val sitToStandSec = phasePrediction.phaseDurationsSec["Sit-To-Stand"] ?: 0f
        val walkFromChairSec = phasePrediction.phaseDurationsSec["Walk-From-Chair"] ?: 0f
        val turningSec = phasePrediction.phaseDurationsSec["Turn-First"] ?: 0f
        val walkToChairSec = phasePrediction.phaseDurationsSec["Walk-To-Chair"] ?: 0f
        val standToSitSec = phasePrediction.phaseDurationsSec["Stand-To-Sit"] ?: 0f
        val turnWalkRatio = if (walkFromChairSec > 0f) {
            turningSec / walkFromChairSec
        } else {
            0f
        }

        return RtmoSeverityFeatures(
            featureNames = FEATURE_NAMES,
            featureVector = floatArrayOf(
                sitToStandSec,
                walkFromChairSec,
                turningSec,
                walkToChairSec,
                standToSitSec,
                turnWalkRatio
            ),
            sitToStandSec = sitToStandSec,
            walkFromChairSec = walkFromChairSec,
            turningSec = turningSec,
            walkToChairSec = walkToChairSec,
            standToSitSec = standToSitSec,
            turnWalkRatio = turnWalkRatio
        )
    }
}

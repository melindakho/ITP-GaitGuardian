package com.example.gaitguardian

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

class FeatureExtraction {
    
    companion object {
        private const val TAG = "FeatureExtraction"
        // Expected ONNX feature order (copied from models/onnx_metadata.json)
        val FEATURE_ORDER = listOf(
            "hip_height",
            "hip_y_velocity",
            "hip_x_velocity",
            "hip_vertical_acceleration",
            "hip_horizontal_acceleration",
            "com_x",
            "com_y",
            "com_x_velocity",
            "com_y_velocity",
            "com_acceleration",
            "left_knee_angle",
            "right_knee_angle",
            "left_hip_angle",
            "right_hip_angle",
            "left_ankle_angle",
            "right_ankle_angle",
            "torso_angle",
            "torso_angle_velocity",
            "knee_coordination",
            "hip_coordination",
            "ankle_coordination",
            "knee_extension_power",
            "hip_extension_power",
            "ankle_power",
            "left_knee_velocity",
            "right_knee_velocity",
            "vertical_momentum",
            "sit_to_stand_power",
            "trunk_flexion_velocity",
            "forward_momentum",
            "forward_acceleration",
            "ankle_distance",
            "heel_distance",
            "toe_distance",
            "base_of_support_width",
            "base_of_support_length",
            "lheel_y_velocity",
            "rheel_y_velocity",
            "lheel_x_velocity",
            "rheel_x_velocity",
            "step_asymmetry",
            "stride_asymmetry",
            "time_since_last_step",
            "step_frequency",
            "step_timing_variability",
            "stride_length_variation",
            "shoulder_angle",
            "shoulder_rotation_velocity",
            "hip_rotation",
            "hip_rotation_velocity",
            "axial_dissociation",
            "axial_dissociation_velocity",
            "head_yaw",
            "head_yaw_velocity",
            "torso_twist",
            "torso_twist_velocity",
            "direction_change_magnitude",
            "turn_preparation_score",
            "mediolateral_sway",
            "anteroposterior_sway",
            "weight_shift_x",
            "weight_shift_y",
            "stability_margin",
            "body_sway",
            "postural_control",
            "movement_complexity",
            "kinetic_energy",
            "potential_energy",
            "total_mechanical_energy",
            "rotational_energy",
            "concentric_power",
            "eccentric_power",
            "total_body_momentum",
            "task_progression",
            "progression_velocity",
            "sit_to_stand_likelihood",
            "walk_from_likelihood",
            "turn_first_likelihood",
            "walk_to_likelihood",
            "turn_second_likelihood",
            "stand_to_sit_likelihood",
            "hip_y_velocity_smooth_5",
            "turn_score_smooth_5",
            "movement_complexity_smooth_5",
            "hip_height_std_5",
            "forward_momentum_std_5",
            "rotation_variation_5",
            "hip_y_velocity_smooth_10",
            "turn_score_smooth_10",
            "movement_complexity_smooth_10",
            "hip_height_std_10",
            "forward_momentum_std_10",
            "rotation_variation_10",
            "hip_y_velocity_smooth_15",
            "turn_score_smooth_15",
            "movement_complexity_smooth_15",
            "hip_height_std_15",
            "forward_momentum_std_15",
            "rotation_variation_15",
            "hip_y_velocity_smooth_30",
            "turn_score_smooth_30",
            "movement_complexity_smooth_30",
            "hip_height_std_30",
            "forward_momentum_std_30",
            "rotation_variation_30",
            "hip_y_acceleration",
            "movement_jerk",
            "rotation_acceleration",
            "sustained_vertical_movement",
            "sustained_forward_movement",
            "sustained_turning"
        )
        const val EXPECTED_NUM_FEATURES = 111

        // Helper to build ordered feature array for ONNX input
        fun buildOrderedFeatureArray(frameFeatures: Map<String, Float>): FloatArray {
            val out = FloatArray(FEATURE_ORDER.size)
            for (j in FEATURE_ORDER.indices) {
                out[j] = frameFeatures[FEATURE_ORDER[j]] ?: 0f
            }
            return out
        }

        fun validateFeatureArray(vec: FloatArray): Boolean {
            if (vec.size != EXPECTED_NUM_FEATURES) {
                Log.e(TAG, "Feature vector size mismatch: ${vec.size} != $EXPECTED_NUM_FEATURES")
                return false
            }
            return true
        }
    }
    
    private fun angleBetween(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val baX = a[0] - b[0]
        val baY = a[1] - b[1]
        
        val bcX = c[0] - b[0]
        val bcY = c[1] - b[1]
        
        val normBa = sqrt(baX * baX + baY * baY)
        val normBc = sqrt(bcX * bcX + bcY * bcY)
        
        if (normBa == 0f || normBc == 0f) return 0f
        
        val dotProduct = baX * bcX + baY * bcY
        val cosAngle = (dotProduct / (normBa * normBc + 1e-6f)).coerceIn(-1f, 1f)
        
        return acos(cosAngle) * 180f / PI.toFloat()
    }
    
    fun extractTugFeatures(landmarksList: List<List<NormalizedLandmark>>, fps: Float): List<Map<String, Float>> {
        if (landmarksList.isEmpty()) {
            Log.w(TAG, "Empty landmarks list, returning empty feature list")
            return emptyList()
        }
        
        val features = mutableListOf<Map<String, Float>>()
        
        val prevVals = mutableMapOf<String, Float>(
            "hip_y" to 0f, "hip_x" to 0f, "shoulder_angle" to 0f, "torso_angle" to 0f,
            "head_yaw" to 0f, "com_x" to 0f, "com_y" to 0f, "com_x_velocity" to 0f, "com_y_velocity" to 0f,
            "forward_momentum" to 0f, "hip_rotation" to 0f, "left_knee_angle" to 0f, "right_knee_angle" to 0f
        )
        
        var lastStepFrame = 0
        val stepFrames = mutableListOf<Int>()
        
        
        for (i in landmarksList.indices) {
            val landmarks = landmarksList[i]
            if (landmarks.size < 33) continue
            
            val prevLandmarks = (i - 1 downTo 0)
                .firstOrNull { landmarksList[it].size >= 33 }
                ?.let { landmarksList[it] }
                ?: landmarks
            fun point(idx: Int): FloatArray = floatArrayOf(landmarks[idx].x(), landmarks[idx].y())
            fun prevPoint(idx: Int): FloatArray = floatArrayOf(prevLandmarks[idx].x(), prevLandmarks[idx].y())
            
            //  Key Body Points
            val lh = point(23)  // left hip
            val rh = point(24)  // right hip  
            val lk = point(25)  // left knee
            val rk = point(26)  // right knee
            val la = point(27)  // left ankle
            val ra = point(28)  // right ankle
            val ls = point(11)  // left shoulder
            val rs = point(12)  // right shoulder
            val lheel = point(29) // left heel
            val rheel = point(30) // right heel
            val ltoe = point(31) // left toe
            val rtoe = point(32) // right toe
            val nose = point(0) // nose
            
            // Previous frame points
            val prevLh = prevPoint(23)
            val prevRh = prevPoint(24)
        
            val hipY = (lh[1] + rh[1]) / 2f
            val hipX = (lh[0] + rh[0]) / 2f
            
            val prevHipY = (prevLh[1] + prevRh[1]) / 2f
            val prevHipX = (prevLh[0] + prevRh[0]) / 2f
            
            val hipYVelocity = (hipY - prevHipY) * fps
            val hipXVelocity = (hipX - prevHipX) * fps
            
            val hipVerticalAcceleration = if (i > 0) hipYVelocity - prevVals["hip_y"]!! else 0f
            val hipHorizontalAcceleration = if (i > 0) hipXVelocity - prevVals["hip_x"]!! else 0f
            
            //  Clinical joint angles
            val leftKneeAngle = angleBetween(lh, lk, la)
            val rightKneeAngle = angleBetween(rh, rk, ra)
            val leftHipAngle = angleBetween(ls, lh, lk)
            val rightHipAngle = angleBetween(rs, rh, rk)
            val leftAnkleAngle = angleBetween(lk, la, ltoe)
            val rightAnkleAngle = angleBetween(rk, ra, rtoe)
            
            //  Trunk kinematics
            val shoulderMid = floatArrayOf((ls[0] + rs[0]) / 2f, (ls[1] + rs[1]) / 2f)
            val hipMid = floatArrayOf((lh[0] + rh[0]) / 2f, (lh[1] + rh[1]) / 2f)
            
            //  torso_angle = angle_between(hip_mid + np.array([0, -1]), hip_mid, shoulder_mid)
            val torsoAngle = angleBetween(
                floatArrayOf(hipMid[0], hipMid[1] - 1f), 
                hipMid, 
                shoulderMid
            )
            val torsoAngleVelocity = torsoAngle - prevVals["torso_angle"]!!
            
            //  Center of mass trajectory
            val comX = (lh[0] + rh[0] + ls[0] + rs[0]) / 4f
            val comY = (lh[1] + rh[1] + ls[1] + rs[1]) / 4f
            val comXVelocity = if (i > 0) (comX - prevVals["com_x"]!!) * fps else 0f
            val comYVelocity = if (i > 0) (comY - prevVals["com_y"]!!) * fps else 0f
            val comAcceleration = sqrt(comXVelocity * comXVelocity + comYVelocity * comYVelocity)
            
            //  Base of support
            val baseOfSupportWidth = abs(lheel[0] - rheel[0])
            val baseOfSupportLength = abs(lheel[1] - rheel[1])
            
            //  Multi-joint coordination patterns
            val kneeCoordination = abs(leftKneeAngle - rightKneeAngle)
            val hipCoordination = abs(leftHipAngle - rightHipAngle)
            val ankleCoordination = abs(leftAnkleAngle - rightAnkleAngle)
            
            //  Sagittal plane kinematics
            val kneeExtensionPower = leftKneeAngle + rightKneeAngle
            val hipExtensionPower = leftHipAngle + rightHipAngle
            val anklePower = leftAnkleAngle + rightAnkleAngle
            
            //  Joint velocity patterns
            val leftKneeVoltage = 0f // placeholder to avoid unused warnings (kept to match shape)
            val leftKneeVelocity = if (i > 0) (leftKneeAngle - prevVals["left_knee_angle"]!!) * fps else 0f
            val rightKneeVelocity = if (i > 0) (rightKneeAngle - prevVals["right_knee_angle"]!!) * fps else 0f
            
            //  Phase-specific features
            val verticalMomentum = abs(hipYVelocity)
            val sitToStandPower = (kneeExtensionPower * verticalMomentum) / (torsoAngle + 1e-6f)
            val trunkFlexionVelocity = abs(torsoAngleVelocity)
            
            //  Walking phase features
            val ankleDistance = abs(la[0] - ra[0])
            val heelDistance = abs(lheel[0] - rheel[0])
            val toeDistance = abs(ltoe[0] - rtoe[0])
            
            //  Step detection velocities
            val prevLheel = prevPoint(29)
            val prevRheel = prevPoint(30)
            val lheelYVelocity = (lheel[1] - prevLheel[1]) * fps
            val rheelYVelocity = (rheel[1] - prevRheel[1]) * fps
            val lheelXVelocity = (lheel[0] - prevLheel[0]) * fps
            val rheelXVelocity = (rheel[0] - prevRheel[0]) * fps
            
            //  Gait asymmetry
            val stepAsymmetry = abs(lheelYVelocity - rheelYVelocity)
            val strideAsymmetry = abs(lheelXVelocity - rheelXVelocity)
            
            //  Forward progression
            val forwardMomentum = (lheelXVelocity + rheelXVelocity) / 2f
            val forwardAcceleration = if (i > 0) forwardMomentum - prevVals["forward_momentum"]!! else 0f
            
            //  Turning kinematics - degrees(atan2(y, x))
            val shoulderAngle = atan2(ls[1] - rs[1], ls[0] - rs[0]) * 180f / PI.toFloat()
            val hipRotation = atan2(lh[1] - rh[1], lh[0] - rh[0]) * 180f / PI.toFloat()
            
            //  Angular velocities with wraparound
            var shoulderRotationVelocity = shoulderAngle - prevVals["shoulder_angle"]!!
            var hipRotationVelocity = hipRotation - prevVals["hip_rotation"]!!
            
            //  Handle angle wraparound (-180 to 180)
            if (abs(shoulderRotationVelocity) > 180f) {
                shoulderRotationVelocity = shoulderRotationVelocity - 360f * sign(shoulderRotationVelocity)
            }
            if (abs(hipRotationVelocity) > 180f) {
                hipRotationVelocity = hipRotationVelocity - 360f * sign(hipRotationVelocity)
            }
            
            //  Axial dissociation
            val axialDissociation = abs(shoulderAngle - hipRotation)
            val axialDissociationVelocity = abs(shoulderRotationVelocity - hipRotationVelocity)
            
            //  Head-trunk coordination
            val headVec = floatArrayOf(nose[0] - shoulderMid[0], nose[1] - shoulderMid[1])
            val headYaw = atan2(headVec[1], headVec[0]) * 180f / PI.toFloat()
            var headYawVelocity = headYaw - prevVals["head_yaw"]!!
            
            //  Handle head yaw wraparound
            if (abs(headYawVelocity) > 180f) {
                headYawVelocity = headYawVelocity - 360f * sign(headYawVelocity)
            }
            
            //  Balance and stability metrics
            val mediolateralSway = abs(comX - hipX)
            val anteroposteriorSway = abs(comY - hipY)
            val weightShiftX = abs(lh[0] - rh[0])
            val weightShiftY = abs(lh[1] - rh[1])
            val stabilityMargin = baseOfSupportWidth - abs(comX - hipX)
            
            //  Step detection logic
            var isStep = false
            if (i > 2) {
                val prevAnkleDistance = abs(landmarksList[i-1][27].x() - landmarksList[i-1][28].x())
                if (ankleDistance > prevAnkleDistance && ankleDistance > 0.12f) {
                    isStep = true
                    lastStepFrame = i
                    stepFrames.add(i)
                }
            }
            
            val timeSinceLastStep = (i - lastStepFrame).toFloat() / fps
            val recentStepCount = stepFrames.count { i - it < 2 * fps }
            val stepFrequency = recentStepCount / 2f
            
            //  Cadence variability - need at least some steps for std calculation
            val recentStepTimes = stepFrames.takeLast(5).map { (i - it).toFloat() / fps }
            val stepTimingVariability = if (recentStepTimes.size > 1) {
                val allTimes = listOf(timeSinceLastStep) + recentStepTimes
                val mean = allTimes.average().toFloat()
                val variance = allTimes.map { (it - mean) * (it - mean) }.average().toFloat()
                sqrt(variance)
            } else 0f
            
            //  Energy and power metrics
            val kineticEnergy = (hipXVelocity * hipXVelocity + hipYVelocity * hipYVelocity) / 2f
            val potentialEnergy = hipY
            val totalMechanicalEnergy = kineticEnergy + potentialEnergy
            val rotationalEnergy = (shoulderRotationVelocity * shoulderRotationVelocity + 
                                   hipRotationVelocity * hipRotationVelocity) / 2f
            val concentricPower = maxOf(0f, hipYVelocity * verticalMomentum)
            val eccentricPower = maxOf(0f, -hipYVelocity * verticalMomentum)
            
            //  Turn preparation score (needed for movement detection)
            val turnPreparationScore = (
                abs(shoulderRotationVelocity) * 0.3f +
                abs(hipRotationVelocity) * 0.3f +
                abs(headYawVelocity) * 0.2f +
                axialDissociationVelocity * 0.2f
            )
            
            //  Movement activity detection - only give temporal priors if actually moving
            val isActivelyMoving = (
                abs(hipYVelocity) > 0.01f ||      // Vertical movement
                abs(hipXVelocity) > 0.01f ||      // Horizontal movement
                abs(forwardMomentum) > 0.01f ||    // Forward progression
                abs(shoulderRotationVelocity) > 5f ||  // Turning
                abs(hipRotationVelocity) > 5f ||       // Pelvic rotation
                verticalMomentum > 0.02f           // Any significant momentum
            )
            
            //  Movement intensity over recent history (last 30 frames = 1 second)
            var recentMovementIntensity = 0f
            if (i >= 30) {
                val recentFrames = landmarksList.subList(i - 30, i)
                val recentHipYValues = recentFrames
                    .filter { it.size >= 33 }
                    .map { (it[23].y() + it[24].y()) / 2f }
                val recentHipXValues = recentFrames
                    .filter { it.size >= 33 }
                    .map { (it[23].x() + it[24].x()) / 2f }
                val recentHipYRange = recentHipYValues.maxOrNull()!! - recentHipYValues.minOrNull()!!
                val recentHipXRange = recentHipXValues.maxOrNull()!! - recentHipXValues.minOrNull()!!
                recentMovementIntensity = recentHipYRange + recentHipXRange
            }
            
            //  Only activate temporal priors if significant movement detected
            val movementMultiplier = if (isActivelyMoving && recentMovementIntensity > 0.05f) 1.0f else 0.0f
            
            
            // ========== TEMPORAL LIKELIHOODS WITH MOVEMENT DETECTION ==========
            
            // Task progression (0.0 to 1.0)
            val taskProgression = i.toFloat() / landmarksList.size.toFloat()
            
            // Progression velocity (0 at middle, 1 at extremes)
            val progressionVelocity = abs(taskProgression - 0.5f) * 2f
            
            // Base temporal likelihoods (only active during movement)
            var sitToStandLikelihood = exp(-((taskProgression - 0.05f).pow(2)) / 0.01f) * movementMultiplier
            var walkFromLikelihood = exp(-((taskProgression - 0.25f).pow(2)) / 0.02f) * movementMultiplier
            var turnFirstLikelihood = exp(-((taskProgression - 0.45f).pow(2)) / 0.01f) * movementMultiplier
            var walkToLikelihood = exp(-((taskProgression - 0.65f).pow(2)) / 0.02f) * movementMultiplier
            var turnSecondLikelihood = exp(-((taskProgression - 0.8f).pow(2)) / 0.01f) * movementMultiplier
            var standToSitLikelihood = exp(-((taskProgression - 0.95f).pow(2)) / 0.01f) * movementMultiplier
            
            // Override: If no movement detected, explicitly zero out ALL motion features
            // This prevents the model from making false predictions based on pose jitter
            val shouldZeroMotionFeatures = movementMultiplier == 0.0f
            
            if (shouldZeroMotionFeatures && taskProgression < 0.15f) {
                // Near start of video with no movement = sitting
                sitToStandLikelihood = 0.9f
                walkFromLikelihood = 0.0f
                turnFirstLikelihood = 0.0f
                walkToLikelihood = 0.0f
                turnSecondLikelihood = 0.0f
                standToSitLikelihood = 0.0f
            } else if (shouldZeroMotionFeatures) {
                // No movement anywhere = sitting/standing still (zero out all movement phases)
                sitToStandLikelihood = 0.5f
                walkFromLikelihood = 0.0f
                turnFirstLikelihood = 0.0f
                walkToLikelihood = 0.0f
                turnSecondLikelihood = 0.0f
                standToSitLikelihood = 0.3f
            }
            
            // ========== END TEMPORAL LIKELIHOODS ==========
            
            //  Derived features - stride length variation
            val recentAnkleDistances = (maxOf(0, i - 10) until i).mapNotNull { j ->
                val lmarks = landmarksList[j]
                if (lmarks.size >= 33) abs(lmarks[27].x() - lmarks[28].x()) else null
            }
            val strideLength = if (recentAnkleDistances.isNotEmpty()) {
                val allDistances = recentAnkleDistances + ankleDistance
                val mean = allDistances.average().toFloat()
                val variance = allDistances.map { (it - mean) * (it - mean) }.average().toFloat()
                sqrt(variance)
            } else 0f
            
            //  More derived features
            val torsoTwist = abs(shoulderAngle - hipRotation)
            val torsoTwistVelocity = abs(shoulderRotationVelocity - hipRotationVelocity)
            
            //  Direction change
            val forwardDirection = if (comXVelocity != 0f) atan2(comYVelocity, comXVelocity) * 180f / PI.toFloat() else 0f
            val prevForwardDirection = if (prevVals["com_x_velocity"]!! != 0f) 
                atan2(prevVals["com_y_velocity"]!!, prevVals["com_x_velocity"]!!) * 180f / PI.toFloat() else 0f
            var directionChangeMagnitude = abs(forwardDirection - prevForwardDirection)
            if (directionChangeMagnitude > 180f) {
                directionChangeMagnitude = 360f - directionChangeMagnitude
            }
            
            //  Body sway - std of lateral positions
            val bodySway = if (i > 5) {
                val recentShoulderX = (maxOf(0, i - 5) until i).mapNotNull { j ->
                    val lmarks = landmarksList[j]
                    if (lmarks.size >= 33) (lmarks[11].x() + lmarks[12].x()) / 2f else null
                }
                val recentHipX = (maxOf(0, i - 5) until i).mapNotNull { j ->
                    val lmarks = landmarksList[j]
                    if (lmarks.size >= 33) (lmarks[23].x() + lmarks[24].x()) / 2f else null
                }
                val positions = recentShoulderX + recentHipX
                if (positions.isNotEmpty()) {
                    val mean = positions.average().toFloat()
                    val variance = positions.map { (it - mean) * (it - mean) }.average().toFloat()
                    sqrt(variance)
                } else 0f
            } else 0f
            
            val posturalControl = 1f / (comAcceleration + 1e-6f)
            
            //  Movement complexity
            val movementComplexity = (
                abs(hipYVelocity) * 0.2f +
                abs(forwardMomentum) * 0.2f +
                turnPreparationScore * 0.2f +
                verticalMomentum * 0.2f +
                axialDissociation * 0.2f
            )
            
            val totalBodyMomentum = sqrt(comXVelocity * comXVelocity + comYVelocity * comYVelocity)
            val effectiveHipYVelocity = if (shouldZeroMotionFeatures) 0f else hipYVelocity
            val effectiveHipXVelocity = if (shouldZeroMotionFeatures) 0f else hipXVelocity
            val effectiveHipVerticalAcceleration = if (shouldZeroMotionFeatures) 0f else hipVerticalAcceleration
            val effectiveHipHorizontalAcceleration = if (shouldZeroMotionFeatures) 0f else hipHorizontalAcceleration
            val effectiveComXVelocity = if (shouldZeroMotionFeatures) 0f else comXVelocity
            val effectiveComYVelocity = if (shouldZeroMotionFeatures) 0f else comYVelocity
            val effectiveComAcceleration = if (shouldZeroMotionFeatures) 0f else comAcceleration
            val effectiveForwardMomentum = if (shouldZeroMotionFeatures) 0f else forwardMomentum
            val effectiveForwardAcceleration = if (shouldZeroMotionFeatures) 0f else forwardAcceleration
            val effectiveVerticalMomentum = if (shouldZeroMotionFeatures) 0f else verticalMomentum
            val effectiveTurnPreparationScore = if (shouldZeroMotionFeatures) 0f else turnPreparationScore
            val effectiveShoulderRotationVelocity = if (shouldZeroMotionFeatures) 0f else shoulderRotationVelocity
            val effectiveHipRotationVelocity = if (shouldZeroMotionFeatures) 0f else hipRotationVelocity
            val effectiveHeadYawVelocity = if (shouldZeroMotionFeatures) 0f else headYawVelocity
            val effectiveLeftKneeVelocity = if (shouldZeroMotionFeatures) 0f else leftKneeVelocity
            val effectiveRightKneeVelocity = if (shouldZeroMotionFeatures) 0f else rightKneeVelocity
            val effectiveLheelYVelocity = if (shouldZeroMotionFeatures) 0f else lheelYVelocity
            val effectiveRheelYVelocity = if (shouldZeroMotionFeatures) 0f else rheelYVelocity
            val effectiveLheelXVelocity = if (shouldZeroMotionFeatures) 0f else lheelXVelocity
            val effectiveRheelXVelocity = if (shouldZeroMotionFeatures) 0f else rheelXVelocity
            val effectiveMovementComplexity = if (shouldZeroMotionFeatures) 0f else movementComplexity
            val effectiveTotalBodyMomentum = if (shouldZeroMotionFeatures) 0f else totalBodyMomentum
            
            val frameFeatures = mapOf(
                "frame" to i.toFloat(),
                
                // CLINICAL GAIT PARAMETERS
                "hip_height" to hipY,
                "hip_y_velocity" to effectiveHipYVelocity,
                "hip_x_velocity" to effectiveHipXVelocity,
                "hip_vertical_acceleration" to effectiveHipVerticalAcceleration,
                "hip_horizontal_acceleration" to effectiveHipHorizontalAcceleration,
                "com_x" to comX,
                "com_y" to comY,
                "com_x_velocity" to effectiveComXVelocity,
                "com_y_velocity" to effectiveComYVelocity,
                "com_acceleration" to effectiveComAcceleration,
                
                // JOINT KINEMATICS
                "left_knee_angle" to leftKneeAngle,
                "right_knee_angle" to rightKneeAngle,
                "left_hip_angle" to leftHipAngle,
                "right_hip_angle" to rightHipAngle,
                "left_ankle_angle" to leftAnkleAngle,
                "right_ankle_angle" to rightAnkleAngle,
                "torso_angle" to torsoAngle,
                "torso_angle_velocity" to torsoAngleVelocity,
                
                // JOINT COORDINATION
                "knee_coordination" to kneeCoordination,
                "hip_coordination" to hipCoordination,
                "ankle_coordination" to ankleCoordination,
                "knee_extension_power" to kneeExtensionPower,
                "hip_extension_power" to hipExtensionPower,
                "ankle_power" to anklePower,
                "left_knee_velocity" to effectiveLeftKneeVelocity,
                "right_knee_velocity" to effectiveRightKneeVelocity,
                
                // PHASE-SPECIFIC FEATURES
                "vertical_momentum" to effectiveVerticalMomentum,
                "sit_to_stand_power" to sitToStandPower,
                "trunk_flexion_velocity" to trunkFlexionVelocity,
                "forward_momentum" to effectiveForwardMomentum,
                "forward_acceleration" to effectiveForwardAcceleration,
                
                // GAIT ANALYSIS
                "ankle_distance" to ankleDistance,
                "heel_distance" to heelDistance,
                "toe_distance" to toeDistance,
                "base_of_support_width" to baseOfSupportWidth,
                "base_of_support_length" to baseOfSupportLength,
                "lheel_y_velocity" to effectiveLheelYVelocity,
                "rheel_y_velocity" to effectiveRheelYVelocity,
                "lheel_x_velocity" to effectiveLheelXVelocity,
                "rheel_x_velocity" to effectiveRheelXVelocity,
                "step_asymmetry" to stepAsymmetry,
                "stride_asymmetry" to strideAsymmetry,
                "time_since_last_step" to timeSinceLastStep,
                "step_frequency" to stepFrequency,
                "step_timing_variability" to stepTimingVariability,
                "stride_length_variation" to strideLength,
                
                // TURNING KINEMATICS
                "shoulder_angle" to shoulderAngle,
                "shoulder_rotation_velocity" to effectiveShoulderRotationVelocity,
                "hip_rotation" to hipRotation,
                "hip_rotation_velocity" to effectiveHipRotationVelocity,
                "axial_dissociation" to axialDissociation,
                "axial_dissociation_velocity" to axialDissociationVelocity,
                "head_yaw" to headYaw,
                "head_yaw_velocity" to effectiveHeadYawVelocity,
                "torso_twist" to torsoTwist,
                "torso_twist_velocity" to torsoTwistVelocity,
                "direction_change_magnitude" to directionChangeMagnitude,
                "turn_preparation_score" to effectiveTurnPreparationScore,
                
                // BALANCE AND STABILITY
                "mediolateral_sway" to mediolateralSway,
                "anteroposterior_sway" to anteroposteriorSway,
                "weight_shift_x" to weightShiftX,
                "weight_shift_y" to weightShiftY,
                "stability_margin" to stabilityMargin,
                "body_sway" to bodySway,
                "postural_control" to posturalControl,
                "movement_complexity" to effectiveMovementComplexity,
                
                // ENERGY AND POWER
                "kinetic_energy" to kineticEnergy,
                "potential_energy" to potentialEnergy,
                "total_mechanical_energy" to totalMechanicalEnergy,
                "rotational_energy" to rotationalEnergy,
                "concentric_power" to concentricPower,
                "eccentric_power" to eccentricPower,
                "total_body_momentum" to effectiveTotalBodyMomentum,
                
                // TEMPORAL CONTEXT
                "task_progression" to taskProgression,
                "progression_velocity" to progressionVelocity,
                "sit_to_stand_likelihood" to sitToStandLikelihood,
                "walk_from_likelihood" to walkFromLikelihood,
                "turn_first_likelihood" to turnFirstLikelihood,
                "walk_to_likelihood" to walkToLikelihood,
                "turn_second_likelihood" to turnSecondLikelihood,
                "stand_to_sit_likelihood" to standToSitLikelihood
            )
            
            features.add(frameFeatures)
        }
        
        Log.e(TAG, "After main loop: features.size = ${features.size}")
        
        // Convert to DataFrame-like structure for rolling window operations
        val featureKeys = features[0].keys.toList()
        val featureArrays = mutableMapOf<String, MutableList<Float>>()
        
        // Initialize arrays
        for (key in featureKeys) {
            featureArrays[key] = mutableListOf()
        }
        
        // Fill arrays
        for (frameFeatures in features) {
            for (key in featureKeys) {
                featureArrays[key]!!.add(frameFeatures[key] ?: 0f)
            }
        }
        
        //  Multi-scale rolling windows for temporal patterns
        val windows = listOf(5, 10, 15, 30)
        val rollingFeatures = mutableMapOf<String, MutableList<Float>>()
        
        for (window in windows) {
            //  df_features[f'hip_y_velocity_smooth_{window}'] = df_features['hip_y_velocity'].rolling(window, center=True).mean()
            rollingFeatures["hip_y_velocity_smooth_$window"] = rollingMean(featureArrays["hip_y_velocity"]!!, window)
            rollingFeatures["turn_score_smooth_$window"] = rollingMean(featureArrays["turn_preparation_score"]!!, window)
            rollingFeatures["movement_complexity_smooth_$window"] = rollingMean(featureArrays["movement_complexity"]!!, window)
            rollingFeatures["hip_height_std_$window"] = rollingStd(featureArrays["hip_height"]!!, window)
            rollingFeatures["forward_momentum_std_$window"] = rollingStd(featureArrays["forward_momentum"]!!, window)
            rollingFeatures["rotation_variation_$window"] = rollingStd(featureArrays["shoulder_rotation_velocity"]!!, window)
        }
        
        //  Transition detection (sudden changes)
        val hipYAcceleration = diff(featureArrays["hip_y_velocity"]!!)
        val movementJerk = diff(featureArrays["movement_complexity"]!!).map { abs(it) }.toMutableList()
        val rotationAcceleration = diff(featureArrays["shoulder_rotation_velocity"]!!).map { abs(it) }.toMutableList()
        
        //  Phase consistency flags
        val verticalMomentumQuantile = quantile(featureArrays["vertical_momentum"]!!, 0.7)
        val forwardMomentumQuantile = quantile(featureArrays["forward_momentum"]!!.map { abs(it) }, 0.7)
        val turnScoreQuantile = quantile(featureArrays["turn_preparation_score"]!!, 0.7)
        
        val sustainedVerticalMovement = featureArrays["vertical_momentum"]!!.map { 
            if (it > verticalMomentumQuantile) 1f else 0f 
        }.toMutableList()
        val sustainedForwardMovement = featureArrays["forward_momentum"]!!.map { 
            if (abs(it) > forwardMomentumQuantile) 1f else 0f 
        }.toMutableList()
        val sustainedTurning = featureArrays["turn_preparation_score"]!!.map { 
            if (it > turnScoreQuantile) 1f else 0f 
        }.toMutableList()
        
        //  Add temporal features to each frame
        val enhancedFeatures = mutableListOf<Map<String, Float>>()
        
        for (i in features.indices) {
            val frameFeatures = features[i].toMutableMap()
            
            // Add rolling window features for this frame
            for (window in windows) {
                frameFeatures["hip_y_velocity_smooth_$window"] = rollingFeatures["hip_y_velocity_smooth_$window"]!![i]
                frameFeatures["turn_score_smooth_$window"] = rollingFeatures["turn_score_smooth_$window"]!![i]
                frameFeatures["movement_complexity_smooth_$window"] = rollingFeatures["movement_complexity_smooth_$window"]!![i]
                frameFeatures["hip_height_std_$window"] = rollingFeatures["hip_height_std_$window"]!![i]
                frameFeatures["forward_momentum_std_$window"] = rollingFeatures["forward_momentum_std_$window"]!![i]
                frameFeatures["rotation_variation_$window"] = rollingFeatures["rotation_variation_$window"]!![i]
            }
            
            // Add derived features for this frame
            frameFeatures["hip_y_acceleration"] = hipYAcceleration[i]
            frameFeatures["movement_jerk"] = movementJerk[i]
            frameFeatures["rotation_acceleration"] = rotationAcceleration[i]
            frameFeatures["sustained_vertical_movement"] = sustainedVerticalMovement[i]
            frameFeatures["sustained_forward_movement"] = sustainedForwardMovement[i]
            frameFeatures["sustained_turning"] = sustainedTurning[i]
            
            enhancedFeatures.add(frameFeatures)
        }
        
        return enhancedFeatures
    }
    
    private fun rollingMean(values: List<Float>, window: Int): MutableList<Float> {
        val result = mutableListOf<Float>()
        for (i in values.indices) {
            val half = window / 2
            val start = maxOf(0, i - half)
            val end = minOf(values.size, i + half + 1)
            val windowValues = values.subList(start, end)
            result.add(windowValues.average().toFloat())
        }
        return result
    }
    
    private fun rollingStd(values: List<Float>, window: Int): MutableList<Float> {
        val result = mutableListOf<Float>()
        for (i in values.indices) {
            val half = window / 2
            val start = maxOf(0, i - half)
            val end = minOf(values.size, i + half + 1)
            val windowValues = values.subList(start, end)
            if (windowValues.size < 2) {
                result.add(0f)
            } else {
                val mean = windowValues.average().toFloat()
                val variance = windowValues.map { (it - mean) * (it - mean) }.average().toFloat()
                result.add(sqrt(variance))
            }
        }
        return result
    }
    
    private fun diff(values: List<Float>): MutableList<Float> {
        val result = mutableListOf<Float>()
        result.add(0f) // First element has no difference
        for (i in 1 until values.size) {
            result.add(values[i] - values[i - 1])
        }
        return result
    }
    
    private fun quantile(values: List<Float>, q: Double): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val index = (q * (sorted.size - 1)).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }
}

package com.example.gaitguardian.screens.patient

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.ExtraBold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gaitguardian.api.TugMetrics
import com.example.gaitguardian.data.roomDatabase.tug.TUGAnalysis
import com.example.gaitguardian.data.roomDatabase.tug.TUGAssessment
import com.example.gaitguardian.ui.theme.*
import com.example.gaitguardian.viewmodels.PatientViewModel
import com.example.gaitguardian.viewmodels.TugDataViewModel
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultScreen(
    navController: NavController,
//    assessmentTitle: String,
    patientViewModel: PatientViewModel,
    tugViewModel: TugDataViewModel,
//    analysisId: Long? = null,  // NEW: Accept specific analysis ID
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(1) } // 1 = core results, 2 = breakdown

    // Get severity from database - use specific analysis ID if provided
    var latestAnalysis by remember { mutableStateOf<TUGAnalysis?>(null) }

    LaunchedEffect(Unit) {
//        tugViewModel.getLatestTUGAssessment()
        tugViewModel.getLatestTwoDurations()
        latestAnalysis = tugViewModel.getLatestTugAnalysis()

        Log.d("ResultScreen", "Analysis loaded: severity=${latestAnalysis?.severity}, testId=${latestAnalysis?.testId}, totalTime=${latestAnalysis?.timeTaken}")
}

    val onMedication by tugViewModel.onMedication.collectAsState()
    val latestTugAssessment by tugViewModel.latestAssessment.collectAsState()
    val latestTwoDurations by tugViewModel.latestTwoDurations.collectAsState()

    // Use data from the specific analysis, not cached response
    val severity = latestAnalysis?.severity ?: ""
    val totalTime = latestAnalysis?.timeTaken ?: 0.0

    var previousTiming by remember { mutableFloatStateOf(0f) }
    var latestTiming by remember { mutableFloatStateOf(0f) }

    if (latestTwoDurations.size >= 2) {
        latestTiming = latestTwoDurations[0]
        previousTiming = latestTwoDurations[1]
    }

    // Medication update local state
    val hasBeenUpdated = latestTugAssessment?.updateMedication == true
    var hasUpdatedMedication by remember { mutableStateOf(hasBeenUpdated) }
    LaunchedEffect(hasBeenUpdated) { hasUpdatedMedication = hasBeenUpdated }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally

        ) {

        when (currentPage) {
            1 -> {
                // Page 1 - Core Results
                CoreResultsCard(
                    latestAssessment = latestTugAssessment,
                    previousTiming = previousTiming,
                    latestTiming = latestTiming,
                    severity = severity,
                    totalTime = totalTime.toFloat(),
                    medicationOn = onMedication,
                    hasUpdatedMedication = hasUpdatedMedication,
                    onUpdateMedication = {
                        if (!hasUpdatedMedication) {
                            hasUpdatedMedication = true
                            tugViewModel.setOnMedication(!onMedication)
                            tugViewModel.updatePostAssessmentOnMedicationStatus(true)
//                            tugViewModel.getLatestTUGAssessment()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { currentPage = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp)

                ) {
                    Text(
                        text = "Next",
                        color = DefaultColor,
                        fontSize = Heading1,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            2 -> {
                // Page 2 - Breakdown + Comments
                TugBreakdownCard(
                    analysis = latestAnalysis,  // Pass the specific analysis instead of cached metrics
                    latestAssessment = latestTugAssessment
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { currentPage = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonActive),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Back",
                            color = DefaultColor,
                            fontSize = Heading1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
fun CoreResultsCard(
    modifier: Modifier = Modifier,
    latestAssessment: TUGAssessment?,
    previousTiming: Float,
    latestTiming: Float,
    severity: String,
    totalTime: Float,
    medicationOn: Boolean?,
    hasUpdatedMedication: Boolean,
    showUpdateButton: Boolean = true,
    onUpdateMedication: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Latest Assessment Result",
                fontSize = subheading1,
                fontWeight = FontWeight.Bold,
                color = DefaultColor
            )

            Spacer(modifier = Modifier.height(12.dp))


            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Type: ${if (latestAssessment != null) "TUG" else "-"}",
                    color = Color.Black,
                    fontSize = body,
                    fontWeight = FontWeight.Medium
                )
                VerticalDivider()
                val formattedDate = latestAssessment?.dateTime?.let { dateTime ->
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(dateTime))
                } ?: "-"
                Text(
                    text = formattedDate,
                    color = Color.Black,
                    fontSize = body,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Severity
            StatusBox(
                title = "Severity",
                value = severity,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            HorizontalProgressBar(
                previousValue = previousTiming,
                latestValue = totalTime,
                maxValue = maxOf(previousTiming, latestTiming, 30f)
            )

            // Only show medication section and button if flag is true
            if (showUpdateButton) {
                Spacer(modifier = Modifier.height(16.dp))
                StatusBox(
                    title = "Medication",
                    value = if (medicationOn == true) "ON" else "OFF",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUpdateMedication,
                    enabled = !hasUpdatedMedication,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackgroundColor,
                        contentColor = DefaultColor,
                        disabledContainerColor = Color(0xFFE0E0E0),
                        disabledContentColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (hasUpdatedMedication) "Medication Status Updated" else "Update Medication Status",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}



@Composable
fun TugBreakdownCard(
    analysis: TUGAnalysis?,  // Changed from TugMetrics to TUGAnalysis
    latestAssessment: TUGAssessment?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "TUG Breakdown",
                fontSize = subheading1,
                fontWeight = FontWeight.Bold,
                color = DefaultColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Use data from the specific TUGAnalysis database entry
                TugPhaseRow("Sit-to-stand", analysis?.sitToStand ?: 0.0)
                TugPhaseRow("Walk-from-chair", analysis?.walkFromChair ?: 0.0)
                TugPhaseRow("Turning", analysis?.turnFirst ?: 0.0)
                TugPhaseRow("Walk-to-chair", analysis?.walkToChair ?: 0.0)
                TugPhaseRow("Stand-to-sit", analysis?.standToSit ?: 0.0)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Comments:",
                fontSize = subheading1,
                fontWeight = FontWeight.Bold,
                color = DefaultColor
            )
            Text(
                text = latestAssessment?.patientComments ?: "No comment provided",
                color = Color.Black
            )
        }
    }
}

//@Composable
//fun LatestAssessmentResultsCard(
//    modifier: Modifier = Modifier,
//    latestAssessment: TUGAssessment?,
//    previousTiming: Float = 13f,
//    latestTiming: Float,
//    severity: String,
//    totalTime: Float,
//    tugMetrics: TugMetrics? = null,
//    medicationOn: Boolean? = null,
//    showComments: Boolean = true,
//    showDivider: Boolean = true,
//    showMedicationToggle: Boolean = false,
//) {
//
//    val maxVal = maxOf(previousTiming, latestTiming, 30f)
//
//    Card(
//        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
//        modifier = modifier
//    ) {
//        Column(modifier = Modifier.padding(16.dp)) {
//            Text(
//                text = "Assessment Result",
//                fontSize = subheading1,
//                fontWeight = FontWeight.Bold,
//                color = DefaultColor
//            )
//
//            Spacer(modifier = Modifier.height(12.dp))
//
////
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(12.dp),
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text(
//                    text = "Type: ${if (latestAssessment != null) "TUG" else "-"}",
//                    color = Color.Black,
//                    fontSize = body,
//                    fontWeight = FontWeight.Medium
//                )
//                VerticalDivider()
//                Text(
//                    text = "${latestAssessment?.dateTime ?: "-"}",
//                    color = Color.Black,
//                    fontSize = body,
//                    fontWeight = FontWeight.Medium
//                )
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
////
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(16.dp),
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 4.dp)
//            ) {
//                StatusBox(
//                    title = "Severity",
//                    value = severity,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 4.dp)
//                )
//
//                if (!showMedicationToggle) {
//                    StatusBox(
//                        title = "Medication",
//                        value = if (latestAssessment != null) {
//                            val isOn = latestAssessment.onMedication
//                            val wasUpdated = latestAssessment.updateMedication
//                            val displayStatus = if (wasUpdated) !isOn else isOn
//                            if (displayStatus) "ON" else "OFF"
//                        } else {
//                            "-"
//                        },
//                        modifier = Modifier.weight(1f)
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            HorizontalProgressBar(
//                previousValue = previousTiming,
//                latestValue = totalTime.toFloat(),
//                maxValue = maxVal,
//                modifier = Modifier.padding(horizontal = 4.dp)
//            )
//
//            if (showDivider) {
//                Spacer(modifier = Modifier.height(20.dp))
//                HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
//                Spacer(modifier = Modifier.height(20.dp))
//            }
//
//            // TUG Breakdown Section
//            Text(
//                text = "TUG Breakdown",
//                fontSize = subheading1,
//                fontWeight = FontWeight.Bold,
//                color = DefaultColor
//            )
//
//            Spacer(modifier = Modifier.height(12.dp))
//
//            Column(
//                verticalArrangement = Arrangement.spacedBy(8.dp),
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 4.dp)
//            ) {
//                TugPhaseRow("Sit-to-stand", tugMetrics?.sitToStandTime ?: 0.0)
//                TugPhaseRow("Walk-from-chair", tugMetrics?.walkFromChairTime ?: 0.0)
//                TugPhaseRow("Turn-first", tugMetrics?.turnFirstTime ?: 0.0)
//                TugPhaseRow("Walk-to-chair", tugMetrics?.walkToChairTime ?: 0.0)
//                TugPhaseRow("Turn-second", tugMetrics?.turnSecondTime ?: 0.0)
//                TugPhaseRow("Stand-to-sit", tugMetrics?.standToSitTime ?: 0.0)
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            if (showMedicationToggle) {
//                Spacer(modifier = Modifier.height(12.dp))
//
//                Text(
//                    text = "Medication Status",
//                    fontSize = subheading1,
//                    fontWeight = FontWeight.Bold,
//                    color = DefaultColor
//                )
//
//                Spacer(modifier = Modifier.height(8.dp))
//
//                Surface(
//                    color = buttonBackgroundColor,
//                    shape = RoundedCornerShape(8.dp),
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(70.dp)
//                        .padding(vertical = 12.dp),
//                ) {
//                    Box(
//                        contentAlignment = Alignment.Center,
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        Text(
//                            text = if (medicationOn == true) "ON" else "OFF",
////                            text = if (latestAssessment?.onMedication == true) "ON" else "OFF",
//                            color = DefaultColor
//                        )
//                    }
//                }
//            }
//
//            if (showComments) {
//                Spacer(modifier = Modifier.height(16.dp))
//
//                Text(
//                    text = "Comments:",
//                    fontSize = subheading1,
//                    fontWeight = FontWeight.Bold,
//                    color = DefaultColor
//                )
//
//                Spacer(modifier = Modifier.height(8.dp))
//
//                (if (latestAssessment?.patientComments.isNullOrEmpty()) "No comment provided" else latestAssessment?.patientComments)?.let {
//                    Text(
//                        text = it,
//                        color = Color.Black,
//                        fontSize = body
//                    )
//                }
//             }
//        }
//    }
//}


@Composable
fun VerticalDivider(
    color: Color = Color.LightGray,
    thickness: Dp = 1.dp,
    height: Dp = 20.dp
) {
    Box(
        modifier = Modifier
            .width(thickness)
            .height(height)
            .background(color)
    )
}


@Composable
fun StatusBox(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(DefaultColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontWeight = ExtraBold,
                color = DefaultColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                fontSize = Heading1,
                color = DefaultColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HorizontalProgressBar(
    previousValue: Float?,
    latestValue: Float?,
    maxValue: Float = 30f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
//                .height(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (previousValue == null || latestValue == null || (previousValue == 0f && latestValue == 0f)) {
                // No data found case
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data found",
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
//                val difference = latestValue - previousValue
//                val (barColor, statusText) = when {
//                    difference < 0f -> {
//                        Color(0xFF4CAF50) to "You beat your previous time by:\n ${"%.1f".format(kotlin.math.abs(difference))}s"
//                    }
//                    difference > 0f -> {
//                        Color(0xFFE53935) to "Missed your previous timing by:\n ${"%.1f".format(kotlin.math.abs(difference))}s"
//                    }
//                    else -> {
//                        Color(0xFFFFCC80) to "Stable performance"
//                    }
//                }
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .background(barColor),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(
//                        text = statusText,
//                        color = Color.White,
//                        fontSize = 16.sp,
////                        maxLines = 1,
////                        overflow = TextOverflow.Ellipsis
//                        maxLines = 2,
//                        lineHeight = 20.sp
//                    )
//                }
                val difference = latestValue - previousValue
                val (barColor, statusText) = when {
                    difference < 0f -> {
                        Color(0xFF4CAF50) to buildAnnotatedString {
                            append("You beat your previous timing by:\n")
                            withStyle(style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            ) {
                                append("${"%.1f".format(kotlin.math.abs(difference))}s")
                            }
                        }
                    }
                    difference > 0f -> {
                        Color(0xFFE53935) to buildAnnotatedString {
                            append("You missed your previous timing by:\n")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                                append("${"%.1f".format(kotlin.math.abs(difference))}s")
                            }
                        }
                    }
                    else -> {
                        Color(0xFFFFCC80) to AnnotatedString("Stable performance")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(barColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Only show prev/now row if data is available
        if (previousValue != null && latestValue != null && !(previousValue == 0f && latestValue == 0f)) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Prev: ${"%.1f".format(previousValue)}s",
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = "Now: ${"%.1f".format(latestValue)}s",
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TugPhaseRow(
    phaseName: String,
    duration: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$phaseName:",
            fontSize = body,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${String.format("%.1f", duration)}s",
            fontSize = body,
            fontWeight = FontWeight.Bold,
            color = DefaultColor
        )
    }
}

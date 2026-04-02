package com.example.gaitguardian.screens.clinician

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.gaitguardian.data.roomDatabase.patient.Patient
import com.example.gaitguardian.ui.theme.bgColor
import com.example.gaitguardian.ui.theme.buttonBackgroundColor
import com.example.gaitguardian.viewmodels.ClinicianViewModel
import com.example.gaitguardian.viewmodels.PatientViewModel
import com.example.gaitguardian.viewmodels.TugDataViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clinician Home Screen - Landing Page after validating PIN
 * Allows Clinician to see a list of completed assessments by the patient
 * Split into 'Reviewed', 'Pending' , 'Critical' (Labelled as critical when isFlagged is True)
 * Can access specific assessment details by clicking on the 'Review Assessment' button
 * Multi-Select Mark as Reviewed
 */
@Composable
fun ClinicianHomeScreen(
    navController: NavController,
    clinicianViewModel: ClinicianViewModel,
    patientViewModel: PatientViewModel,
    tugViewModel: TugDataViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Load necessary information from ViewModels for updating the UI in this screen
    val patientInfo by patientViewModel.patient.collectAsState()
    val clinicianInfo by clinicianViewModel.clinician.collectAsState()
    val uploadedAssessments by tugViewModel.allTUGAssessments.collectAsState()
    val allTugAnalysis by tugViewModel.allTUGAnalysis.collectAsState()

    // return number of patient assessments that are NOT reviewed by clinician
    val pendingReviews = uploadedAssessments.count { !it.watchStatus }

    // Calculate critical reviews (if critical means isFlagged is True)
    val criticalReviews = uploadedAssessments.count { assessment ->
        val matchedAnalysis = allTugAnalysis.find { it.testId == assessment.testId }
        val flagStatus = matchedAnalysis?.isFlagged ?: false
        isCriticalReview(assessment.watchStatus, flagStatus)
    }

    var selectedVideoIds by remember { mutableStateOf(setOf<String>()) }
    var showPendingVideos by remember { mutableStateOf(false) }
    var showCriticalVideos by remember { mutableStateOf(false) }
    var showReviewedVideos by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        tugViewModel.backfillMissingVideoDurations(context)
    }

    // Used to update the list of filtered assessments
    val filteredVideos = when {
        showCriticalVideos -> {
            uploadedAssessments.filter { assessment ->
                val matchedAnalysis = allTugAnalysis.find { it.testId == assessment.testId }
                val flagStatus = matchedAnalysis?.isFlagged ?: false
                isCriticalReview(assessment.watchStatus, flagStatus)
            }
        }
        showPendingVideos -> uploadedAssessments.filter { !it.watchStatus }
        showReviewedVideos -> uploadedAssessments.filter { it.watchStatus }
        else -> uploadedAssessments
    }

    Spacer(Modifier.height(30.dp))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = if (selectedVideoIds.isNotEmpty()) 80.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ClinicianHeader(
                    clinicianName = clinicianInfo?.name ?: "Clinician",
                    patient = patientInfo ?: Patient(id = 2, name = "Patient", age = 18),
                    pendingReviews = pendingReviews,
                    criticalReviews = criticalReviews
                )
            }
            item {
                VideoReviewsSummaryCard(
                    totalTests = uploadedAssessments.count(),
                    pendingTests = pendingReviews,
                    criticalTests = criticalReviews,
                    showOnlyReviewed = showReviewedVideos,
                    showOnlyPending = showPendingVideos,
                    showOnlyCritical = showCriticalVideos,
                    onFilterToggle = { filterType ->
                        when (filterType) {
                            "reviewed" -> {
                                showReviewedVideos = true
                                showPendingVideos = false
                                showCriticalVideos = false
                            }

                            "pending" -> {
                                showReviewedVideos = false
                                showPendingVideos = true
                                showCriticalVideos = false
                            }

                            "critical" -> {
                                showReviewedVideos = false
                                showPendingVideos = false
                                showCriticalVideos = true
                            }
                        }
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Patient's Assessment Records",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2D3748)
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF718096))
                }
            }

            if (filteredVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                showReviewedVideos -> "No reviewed assessments to display."
                                showCriticalVideos -> "No critical assessments to review."
                                showPendingVideos -> "No pending assessments to review."
                                else -> "No assessments available."
                            },
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(filteredVideos) { video ->
                    val finalMedicationState = video.onMedication != video.updateMedication
                    val matchedAnalysis = allTugAnalysis.find { it.testId == video.testId }
                    val finalSeverity = matchedAnalysis?.severity ?: "N/A"
                    val flagStatus = matchedAnalysis?.isFlagged ?: false
                    val isCritical = isCriticalReview(video.watchStatus, flagStatus)

                    TUGVideoItem(
                        navController = navController,
                        tugViewModel = tugViewModel,
                        testId = video.testId,
                        dateTime = SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.getDefault()
                        ).format(Date(video.dateTime)),
                        medication = finalMedicationState,
                        severity = finalSeverity,
                        watchStatus = if (video.watchStatus) "Reviewed" else "Pending",
                        isCritical = isCritical,
                        isSelected = selectedVideoIds.contains(video.testId),
                        onSelectionChanged = { isSelected ->
                            selectedVideoIds = if (isSelected) {
                                selectedVideoIds + video.testId
                            } else {
                                selectedVideoIds - video.testId
                            }
                        }
                    )
                }
            }
        }

        if (selectedVideoIds.isNotEmpty()) {
            MultiSelectControls(
                selectedCount = selectedVideoIds.size,
                onMarkAsWatched = {
                    selectedVideoIds.forEach { videoId ->
                        tugViewModel.markMultiAsReviewed(videoId)
                    }
                    selectedVideoIds = setOf()
                },
                onCancel = {
                    selectedVideoIds = setOf()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun MultiSelectControls(
    selectedCount: Int,
    onMarkAsWatched: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4299E1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 8.dp
                )
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$selectedCount video${if (selectedCount != 1) "s" else ""} selected",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Color.White)
                )
            ) {
                Text("Cancel", color = Color.White)
            }

            Button(
                onClick = onMarkAsWatched,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                )
            ) {
                Text("Mark as Reviewed", color = Color(0xFF4299E1))
            }
        }
    }
}


@Composable
fun VideoReviewsSummaryCard(
    totalTests: Int,
    pendingTests: Int,
    criticalTests: Int,
    showOnlyReviewed: Boolean,
    showOnlyPending: Boolean,
    showOnlyCritical: Boolean,
    onFilterToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Overview",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D3748)
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF718096))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VideoOverviewStats(
                    value = (totalTests - pendingTests).toString(),
                    label = "Reviewed",
                    isSelected = showOnlyReviewed,
                    onClick = { onFilterToggle("reviewed") },
                    backgroundColor = Color(0xFFE6FFFA),
                    textColor = Color(0xFF234E52),
                    modifier = Modifier.weight(1f)
                )
                VideoOverviewStats(
                    value = pendingTests.toString(),
                    label = "Needs Review",
                    isSelected = showOnlyPending,
                    onClick = { onFilterToggle("pending") },
                    backgroundColor = Color(0xFFFEF5E7),
                    textColor = Color(0xFFB7791F),
                    modifier = Modifier.weight(1f)
                )
                VideoOverviewStats(
                    value = criticalTests.toString(),
                    label = "Critical",
                    isSelected = showOnlyCritical,
                    onClick = { onFilterToggle("critical") },
                    backgroundColor = Color(0xFFFFF5F5),
                    textColor = Color(0xFFC53030),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun VideoOverviewStats(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    backgroundColor: Color = Color(0xFFE6FFFA),
    textColor: Color = Color(0xFF234E52),
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(
                if (isSelected) backgroundColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) textColor else Color(0xFF2D3748)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) textColor else Color(0xFF718096)
        )
    }
}

@Composable
fun TUGVideoItem(
    navController: NavController,
    tugViewModel: TugDataViewModel,
    testId: String,
    dateTime: String,
    medication: Boolean,
    severity: String,
    watchStatus: String,
    isCritical: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else if (isCritical) 6.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFFEBF8FF)
                isCritical -> Color(0xFFFFF5F5)
                else -> Color.White
            }
        ),
        border = when {
            isSelected -> BorderStroke(2.dp, Color(0xFF4299E1))
            isCritical -> BorderStroke(2.dp, Color(0xFFFC8181))
            else -> null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (watchStatus == "Pending") {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectionChanged,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4299E1)
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TUG #${tugViewModel.getDisplayNumberForId(testId)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D3748)
                    )
                }

                VideoWatchStatus(watchStatus, isCritical)
            }

            Text(
                text = dateTime,
                fontSize = 12.sp,
                color = Color(0xFF2D3748),
                modifier = Modifier.padding(start = 48.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                buildAnnotatedString {
                    append("Medication: ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(if (medication) "ON" else "OFF")
                    }
                },
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 48.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    buildAnnotatedString {
                        append("Video Severity Rating: ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(severity)
                        }
                    },
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isSelected) {
                Button(
                    onClick = {
                        navController.navigate("clinician_detailed_patient_view_screen/${testId}")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCritical) Color(0xFFE53E3E) else buttonBackgroundColor
                    )
                ) {
                    Text(
                        text = if (isCritical) "Review Critical Assessment" else "Review Assessment",
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun VideoWatchStatus(watchStatus: String, isCritical: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Card(
            modifier = Modifier
                .height(24.dp)
                .width(80.dp),
            shape = RoundedCornerShape(5.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (watchStatus == "Reviewed") Color(0xFFC6F6D5) else Color(
                    0xFFFEEBC8
                ),
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = watchStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (watchStatus == "Reviewed") Color(0xFF2F855A) else Color(0xFFDD6B20),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (isCritical) {
            Card(
                modifier = Modifier
                    .height(24.dp)
                    .width(80.dp),
                shape = RoundedCornerShape(5.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE53E3E),
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CRITICAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

fun isCriticalReview(watchStatus: Boolean, flagStatus: Boolean): Boolean {
    return !watchStatus && flagStatus // if not watched/reviewed but isFlagged ( got 1 second diff)
}

@Composable
fun ClinicianHeader(
    clinicianName: String,
    patient: Patient,
    pendingReviews: Int,
    criticalReviews: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Welcome back,",
                fontSize = 16.sp,
                color = Color(0xFF718096),
                fontWeight = FontWeight.Normal
            )
            Text(
                text = clinicianName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFFE2E8F0),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Currently reviewing",
                        fontSize = 14.sp,
                        color = Color(0xFF718096),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = patient.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2D3748)
                        )
                        Text(
                            text = "• Age ${patient.age}",
                            fontSize = 14.sp,
                            color = Color(0xFF718096)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Alert boxes row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Critical Reviews Box
                if (criticalReviews > 0) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF5F5)
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Critical reviews",
                                tint = Color(0xFFE53E3E),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "$criticalReviews Critical",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53E3E)
                                )
                                Text(
                                    text = "Urgent review",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC53030)
                                )
                            }
                        }
                    }
                }

                // Pending Reviews Box
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pendingReviews > 0) Color(0xFFFEF5E7) else Color(
                            0xFFF0FFF4
                        )
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Pending reviews",
                            tint = if (pendingReviews > 0) Color(0xFFDD6B20) else Color(0xFF38A169),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = if (pendingReviews > 0)
                                    "$pendingReviews Pending"
                                else
                                    "All Clear",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingReviews > 0) Color(0xFFDD6B20) else Color(
                                    0xFF38A169
                                )
                            )
                            Text(
                                text = if (pendingReviews > 0)
                                    "video${if (pendingReviews != 1) "s" else ""} to review"
                                else
                                    "No pending videos",
                                fontSize = 11.sp,
                                color = if (pendingReviews > 0) Color(0xFFB7791F) else Color(
                                    0xFF2F855A
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

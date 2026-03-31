package com.example.gaitguardian.screens.patient

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.gaitguardian.analysis.VideoViewType
import com.example.gaitguardian.ui.theme.*
import com.example.gaitguardian.viewmodels.PatientViewModel
import com.example.gaitguardian.viewmodels.TugDataViewModel

@Composable
fun AssessmentInfoScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    patientViewModel: PatientViewModel,
    tugViewModel: TugDataViewModel
) {
    val context = LocalContext.current
    val vibrator = remember {
        ContextCompat.getSystemService(context, Vibrator::class.java)
    }

    val firstPrivacyCheck by patientViewModel.firstPrivacyCheck.collectAsState()
    val onMedication by tugViewModel.onMedication.collectAsState()
    val selectedVideoViewType by tugViewModel.selectedVideoViewType.collectAsState()

    // Haptic feedback function
    fun provideHapticFeedback() {
        vibrator?.let { vib ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vib.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(50)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(screenPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
//            Text(
//                assessmentTitle,
//                style = MaterialTheme.typography.headlineSmall,
//                color = Color.Black
//            )
//
//            Spacer(Modifier.height(24.dp))

            Text(
                "Tag medication status",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 28.sp,
                color = Color.Black
            )
//            Spacer(Modifier.height(8.dp))
//
//            Text(
//                "Please tag your medication state before starting the test. This helps us understand how your medication affects your mobility.",
//                style = MaterialTheme.typography.bodyMedium,
//                color = Color.Black
//            )

            Spacer(Modifier.height(16.dp))
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MedicationStatusButton(
                        text = "ON",
                        isSelected = onMedication,
                        onClick = {
                            provideHapticFeedback() // Add haptic feedback
                            tugViewModel.setOnMedication(true)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MedicationStatusButton(
                        text = "OFF",
                        isSelected = !onMedication,
                        onClick = {
                            provideHapticFeedback() // Add haptic feedback
                            tugViewModel.setOnMedication(false)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    "Choose camera view",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 24.sp,
                    color = Color.Black
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VideoViewTypeButton(
                        text = VideoViewType.SIDE.displayLabel,
                        isSelected = selectedVideoViewType == VideoViewType.SIDE,
                        onClick = {
                            provideHapticFeedback()
                            tugViewModel.setSelectedVideoViewType(VideoViewType.SIDE)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VideoViewTypeButton(
                        text = VideoViewType.FRONT.displayLabel,
                        isSelected = selectedVideoViewType == VideoViewType.FRONT,
                        onClick = {
                            provideHapticFeedback()
                            tugViewModel.setSelectedVideoViewType(VideoViewType.FRONT)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
//            OutlinedTextField(
//                value = comments,
//                onValueChange = {
//                    tugViewModel.setAssessmentComment(it)
//                },
//                placeholder = { Text("Additional comments", color = Color.Black.copy(alpha = 0.5f)) },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(120.dp),
//                textStyle = LocalTextStyle.current.copy(color = Color.Black)
//            )
//            }
                val selectedComments by tugViewModel.selectedComments.collectAsState()

                AdditionalCommentsList(
                    options = listOf(
                        "Felt dizzy",
                        "Needed assistance",
                        "Used walker",
                        "Felt strong",
                        "Tremors",
                    ),
                    selectedOptions = selectedComments,
                    onSelectionChange = { option ->
                        provideHapticFeedback() // Add haptic feedback
                        tugViewModel.toggleComment(option)
                    }
                )
            }
        }
        Button(
            onClick = {
                provideHapticFeedback() // Add haptic feedback
                if(!firstPrivacyCheck){
                    navController.navigate("video_privacy_screen")
                }
                else{
                    navController.navigate("new_cam_screen")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ButtonActive),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
//                .padding(vertical = 16.dp)
                .padding(vertical = 8.dp)
                .size(width = 150.dp, height = 50.dp)
        ) {
            Text("Continue", color = Color.Black, fontSize = 18.sp)
        }
    }
}

@Composable
fun VideoViewTypeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) ButtonActive else Color.White,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .padding(vertical = 8.dp)
            .size(width = 150.dp, height = 50.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MedicationStatusButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) ButtonActive else Color.White,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .padding(vertical = 8.dp)
            .size(width = 150.dp, height = 50.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AdditionalCommentsList(
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            "Additional Comments",
            color = Color.Black,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = option in selectedOptions
                OutlinedButton(
                    onClick = { onSelectionChange(option) },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, if (isSelected) ButtonActive else Color.Gray),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) ButtonActive.copy(alpha = 0.2f) else Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
//                        .padding(vertical = .dp)
                        .size(width = 150.dp, height = 50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(option, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

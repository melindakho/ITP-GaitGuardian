package com.example.gaitguardian.screens.clinician

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gaitguardian.data.roomDatabase.tug.TUGAnalysis
import com.example.gaitguardian.ui.theme.bgColor
import com.example.gaitguardian.viewmodels.TugDataViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/**
 * Clinician Performance Screen - Graphs of TUG Assessments (Comparison of durations across assessments)
 * Populated based on the number of completed assessments
 * Filter to individual sub-task comparison
 */
@Composable
fun PerformanceScreen(
    tugViewModel: TugDataViewModel,
) {
    val allSubtasks by tugViewModel.allTUGAnalysis.collectAsState()
    var selectedTask by remember { mutableStateOf("All Tasks") }

    Column(
        modifier = Modifier
            .background(bgColor)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TaskDropdownFilter(
            selectedTask = selectedTask,
            onTaskSelected = { selectedTask = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        PerformanceChart(
            subtasks = allSubtasks,
            selectedTask = selectedTask,
            modifier = Modifier.fillMaxSize()

        )
    }
}

@Composable
fun PerformanceChart(
    subtasks: List<TUGAnalysis>,
    selectedTask: String,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val taskToValue: Map<String, (TUGAnalysis) -> Float> = mapOf(
//        Map "Sit-to-Stand" to sitToStand so that I can retrieve the values direct
        "Sit-to-Stand" to { it.sitToStand.toFloat() },
        "Walk from Chair" to { it.walkFromChair.toFloat() },
        "Turning" to { it.turnFirst.toFloat() },
        "Walk to Chair" to { it.walkToChair.toFloat() },
        "Stand-to-Sit" to { it.standToSit.toFloat() },
        "All Tasks" to { it.timeTaken.toFloat() }
    )
    LaunchedEffect(selectedTask) {
        val extractTaskValues = taskToValue[selectedTask]
        if (extractTaskValues != null) {
            val xValues =
                subtasks.indices.map { (it + 1).toFloat() } // because now using String, use the index instead but +1 so it starts from 1
//            val xValues = subtasks.map { it.testId }
            val yValues = subtasks.map { extractTaskValues(it) }
            modelProducer.runTransaction {
                lineSeries {
                    series(xValues, yValues)
                }
            }
        }
    }
    JetpackComposeBasicLineChart(modelProducer, selectedTask, modifier)


}

@Composable
private fun JetpackComposeBasicLineChart(
    modelProducer: CartesianChartModelProducer,
    selectedTask: String,
    modifier: Modifier = Modifier,
) {
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(Color.Blue)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.point(
                shapeComponent(fill(Color.Blue), CorneredShape.Pill)
            )
        )
    )
    // Set colour text value for axis
    val axisValueColor = 0xFF000000.toInt() // Black (ARGB format)
    val valueComponent = TextComponent(
        color = axisValueColor, // since they only allow Int
    )

    val xAxis = HorizontalAxis.rememberBottom(
        title = selectedTask,
        titleComponent = TextComponent(),
        label = valueComponent
    )

    val yAxis = VerticalAxis.rememberStart(
        title = "Time Taken",
        titleComponent = TextComponent(),
        label = valueComponent
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(listOf(line))
            ),
            startAxis = yAxis,
            bottomAxis = xAxis,
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(300.dp), // Set a minimum height

    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDropdownFilter(
    selectedTask: String,
    onTaskSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val allTasks = listOf(
        "All Tasks",
        "Sit-to-Stand",
        "Walk from Chair",
        "Turning",
        "Walk to Chair",
        "Stand-to-Sit"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Column()
        {
            Text(
                text = "Filter by Task",
                color = Color.DarkGray,
                fontSize = 14.sp,
            )
            OutlinedTextField(
                value = selectedTask,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.DarkGray,
                    unfocusedLabelColor = Color.DarkGray,
                    focusedBorderColor = Color(0xFFDDDDDD),
                    unfocusedBorderColor = Color(0xFFDDDDDD),
                    focusedContainerColor = Color(0xFFF9F9F9),
                    unfocusedContainerColor = Color(0xFFF9F9F9),
                    disabledContainerColor = Color(0xFFF9F9F9),
                    disabledBorderColor = Color(0xFFDDDDDD),
                    disabledTextColor = Color.Black,
                    disabledLabelColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFFF9F9F9), shape = RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp)),
            containerColor = Color(0xFFF9F9F9),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            allTasks.forEach { task ->
                DropdownMenuItem(
                    text = {
                        Text(
                            task,
                            fontSize = 14.sp,
                            fontWeight = if (task == selectedTask) FontWeight.Bold else FontWeight.Normal,
                            color = if (task == selectedTask) Color(0xFF1565C0) else Color.Black
                        )
                    },
                    onClick = {
                        onTaskSelected(task)
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (task == selectedTask) Color(0xFFE3F2FD) else Color.Transparent
                        )
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                )
            }
        }
    }
}


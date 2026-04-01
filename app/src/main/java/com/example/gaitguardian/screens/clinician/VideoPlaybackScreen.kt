package com.example.gaitguardian.screens.clinician

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gaitguardian.data.roomDatabase.tug.subtaskDuration
import com.example.gaitguardian.viewmodels.TugDataViewModel
import android.app.Activity
import android.content.res.Configuration
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.example.gaitguardian.ui.theme.bgColor
import com.example.gaitguardian.ui.theme.buttonBackgroundColor
import java.io.File
/**
 * Clinician Video Playback Screen -
 * Review the video playback of the TUG assessment
 * Interactive UI which highlights the subtask being performed
 * Clickable Buttons to Jump to Specific Subtask
 * Can do portrait and landscape orientation
 */
@Composable
fun VideoPlaybackScreen(
    tugViewModel: TugDataViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    // shared playback states to control ExoPlayer
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var currentPosition by rememberSaveable { mutableStateOf(0L) }
    var duration by rememberSaveable { mutableStateOf(0L) }

    val assessment by tugViewModel.selectedTUGAssessment.collectAsState()
    val subtaskDuration by tugViewModel.subtaskDuration.collectAsState()
    val subtaskJumpTimings = subtaskDuration?.let { prepareSubtaskJumpTimings(it) }
    val videoTitle = assessment?.videoTitle ?: return
    val videoFile = File(videoTitle)
    val videoUri = videoFile.toUri()

    // Hoist ExoPlayer to parent component so that it does not recreate the ExoPlayer instance when screen rotate
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))

            prepare()
            seekTo(currentPosition)
            if(isPlaying) play() else pause()
        }
    }

    DisposableEffect(lifecycleOwner) { // to control ExoPlayer lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Position updater
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            isPlaying = exoPlayer.isPlaying
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    )
    {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LandscapeVideoLayout(
                exoPlayer = exoPlayer,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                jumpTimestamps = subtaskJumpTimings,
                navController = navController
            )
        } else {
            PortraitVideoLayout(
                exoPlayer = exoPlayer,
                currentPosition = currentPosition,
                duration = duration,
                jumpTimestamps = subtaskJumpTimings
            )
        }
    }
}

@Composable
fun PortraitVideoLayout(
    exoPlayer: ExoPlayer,
    currentPosition: Long,
    duration: Long,
    jumpTimestamps: List<Triple<String, Long, Long>>?
) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        600
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
//            Button(onClick = onPlayPauseClick) {
//                Text(if (isPlaying) "Pause" else "Play")
//            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { value -> exoPlayer.seekTo(value.toLong()) },
                    valueRange = 0f..(duration.coerceAtLeast(0L)).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFC9E4F),
                        activeTrackColor = buttonBackgroundColor,
                        inactiveTrackColor = Color.Gray
                )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTimeSeconds(currentPosition), color = Color.Black)
                    Text(formatTimeSeconds(duration), color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Jump Buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.5.dp),
            maxItemsInEachRow = 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            jumpTimestamps?.forEach { (label, startMs, endMs) ->
                val isActive = currentPosition in startMs..endMs

                Button(
                    onClick = { exoPlayer.seekTo(startMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = if (isActive)
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
                    else
                        ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            text = "${(endMs - startMs) / 1000f} s",
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LandscapeVideoLayout(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    jumpTimestamps: List<Triple<String, Long, Long>>?,
    navController: NavHostController
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // State to control overlay visibility
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Hide system bars for full-screen video
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }

        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .background(Color.Black)
            .clickable { controlsVisible = true } // Show controls on tap
    ) {
        // Fullscreen video
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )
        // Top-right current subtask text
        jumpTimestamps?.let { jumps ->
            // Since my Triple is (subtask label, start time, end time), check for t
            // the current position of the video to be within the start and end time and extract the label of it
            val currentSubtask = jumps.find { currentPosition in it.second..it.third }?.first ?: ""
            Text(
                text = "Subtask: $currentSubtask",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        // Animated overlay controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val window = activity?.window
                            val controller = window?.let {
                                WindowInsetsControllerCompat(it, it.decorView)
                            }
                            controller?.show(WindowInsetsCompat.Type.systemBars())
                            navController.popBackStack()
                        }
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = onPlayPauseClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A148C).copy(alpha = 0.9f)
                        )
                    ) {
                        Text(if (isPlaying) "Pause" else "Play", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        jumpTimestamps?.forEach { (label, startMs, endMs) ->
                            val isActive = currentPosition in startMs..endMs

                            // Each button takes roughly 1/3 of the available width
                            Button(
                                onClick = { exoPlayer.seekTo(startMs) },
                                colors = if (isActive)
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
                                else
                                    ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        maxLines = 1,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${(endMs - startMs) / 1000f} s",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }


                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { value -> exoPlayer.seekTo(value.toLong()) },
                            valueRange = 0f..(duration.coerceAtLeast(0L)).toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF4A148C)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTimeSeconds(currentPosition), color = Color.White)
                            Text(formatTimeSeconds(duration), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun formatTimeSeconds(ms: Long): String { //  milliseconds to seconds conversion
    return String.format("%.2f s", ms.toDouble() / 1000.0)
}

fun prepareSubtaskJumpTimings(subtask: subtaskDuration): List<Triple<String, Long, Long>> {
    // Using the list of subtask duration , append to a list that holds the label of the that particular subtask, the start time and end time
    val labels = listOf(
        "Sit to Stand",
        "Walk from Chair",
        "Turning",
        "Walk to Chair",
        "Stand to Sit"
    )

    val durations = listOf(
        subtask.sitToStand,
        subtask.walkFromChair,
        subtask.turnFirst,
        subtask.walkToChair,
        subtask.standToSit
    )

    val result = mutableListOf<Triple<String, Long, Long>>()
    var runningTotal = 0.0

    for (i in durations.indices) {
        val startMs = (runningTotal * 1000).toLong()
        runningTotal += durations[i]
        val endMs = (runningTotal * 1000).toLong()
        result.add(Triple(labels[i], startMs, endMs))
    }

    Log.d("VideoPlayer", "Subtask Ranges: $result")
    return result
}

package com.kexora.soniclet.tools

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneCutterDialog(
    song: Song,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val totalDurationMs = song.duration.coerceAtLeast(1000L)

    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(totalDurationMs.coerceAtMost(30000L).toFloat()) }
    var selectedTarget by remember { mutableStateOf(RingtoneTarget.PHONE_CALL) }
    var customTitle by remember { mutableStateOf("${song.title} (Trimmed)") }
    var isProcessing by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Audio Preview Player
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        isPreviewPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    fun togglePreview() {
        if (isPreviewPlaying) {
            stopPreview()
        } else {
            try {
                stopPreview()
                val player = MediaPlayer().apply {
                    setDataSource(context, song.contentUri)
                    prepare()
                    seekTo(startMs.toInt())
                    start()
                }
                mediaPlayer = player
                isPreviewPlaying = true

                scope.launch {
                    while (isPreviewPlaying) {
                        delay(100)
                        val curr = try { player.currentPosition } catch (_: Exception) { -1 }
                        if (curr < 0 || curr >= endMs.toInt()) {
                            stopPreview()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot preview audio: ${e.message}", Toast.LENGTH_SHORT).show()
                stopPreview()
            }
        }
    }

    fun formatMs(ms: Float): String {
        val totalSecs = (ms / 1000).toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        val millis = (ms % 1000).toInt() / 100
        return "%02d:%02d.%d".format(m, s, millis)
    }

    fun proceedWithTrimAndSet(target: RingtoneTarget) {
        stopPreview()
        isProcessing = true
        RingtoneCutterUtil.trimAndSetRingtone(
            context = context,
            inputUri = song.contentUri,
            title = customTitle.ifBlank { song.title },
            startMs = startMs.toLong(),
            endMs = endMs.toLong(),
            target = target,
            onSuccess = { appliedTarget, _ ->
                isProcessing = false
                val msg = when (appliedTarget) {
                    RingtoneTarget.PHONE_CALL -> "Phone Call Ringtone set successfully! 📞"
                    RingtoneTarget.NOTIFICATION -> "Notification Sound set successfully! 🔔"
                    RingtoneTarget.ALARM -> "Alarm Sound set successfully! ⏰"
                    RingtoneTarget.SAVE_ONLY -> "Sound saved to Ringtones storage! 📁"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onDismissRequest()
            },
            onError = { errMsg ->
                isProcessing = false
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    // Permission explanation dialog if system settings write is needed
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To set this sound as your default Call Ringtone or Notification Sound, Android requires 'Modify System Settings' permission.\n\nTap 'Grant Permission' to open Settings, turn on 'Allow modifying system settings', then tap Set Sound.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        RingtoneCutterUtil.openWriteSettings(context)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        // Fallback to saving to storage only
                        proceedWithTrimAndSet(RingtoneTarget.SAVE_ONLY)
                    }
                ) {
                    Text("Save to Storage Only")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) {
                stopPreview()
                onDismissRequest()
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.92f)
            .imePadding(),
        icon = { Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ringtone & Audio Cutter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section: Sound Target Selection (Ringtone vs Notification vs Alarm)
                Text(
                    text = "Choose Sound Type:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option 1: Phone Ringtone
                    val isRingtone = selectedTarget == RingtoneTarget.PHONE_CALL
                    Surface(
                        onClick = { selectedTarget = RingtoneTarget.PHONE_CALL },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRingtone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isRingtone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RingVolume,
                                contentDescription = null,
                                tint = if (isRingtone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Call Ringtone",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isRingtone) FontWeight.Bold else FontWeight.Normal),
                                color = if (isRingtone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Option 2: Notification Sound
                    val isNotif = selectedTarget == RingtoneTarget.NOTIFICATION
                    Surface(
                        onClick = { selectedTarget = RingtoneTarget.NOTIFICATION },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNotif) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isNotif) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = if (isNotif) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Notification",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isNotif) FontWeight.Bold else FontWeight.Normal),
                                color = if (isNotif) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Option 3: Alarm Sound
                    val isAlarm = selectedTarget == RingtoneTarget.ALARM
                    Surface(
                        onClick = { selectedTarget = RingtoneTarget.ALARM },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAlarm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isAlarm) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = null,
                                tint = if (isAlarm) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Alarm",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isAlarm) FontWeight.Bold else FontWeight.Normal),
                                color = if (isAlarm) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Range time display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Start Point", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMs(startMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }

                        // Preview Audio Button
                        FilledTonalButton(
                            onClick = { togglePreview() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPreviewPlaying) "Pause" else "Preview", style = MaterialTheme.typography.labelSmall)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("End Point", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMs(endMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val clipSec = (endMs - startMs) / 1000f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clip Duration: %.1f seconds".format(clipSec),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Quick duration presets
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(15f, 30f).forEach { sec ->
                            Surface(
                                onClick = {
                                    val newEnd = (startMs + sec * 1000f).coerceAtMost(totalDurationMs.toFloat())
                                    endMs = newEnd
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${sec.toInt()}s",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Start point slider
                Column {
                    Text("Start Point (Drag to adjust)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = startMs,
                        onValueChange = {
                            startMs = it.coerceAtMost(endMs - 1000f)
                            if (isPreviewPlaying) stopPreview()
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // End point slider
                Column {
                    Text("End Point (Drag to adjust)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = endMs,
                        onValueChange = {
                            endMs = it.coerceAtLeast(startMs + 1000f)
                            if (isPreviewPlaying) stopPreview()
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Custom Title input
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Sound Name", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Trimming and setting sound...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing,
                onClick = {
                    if (selectedTarget != RingtoneTarget.SAVE_ONLY && !RingtoneCutterUtil.hasWriteSettingsPermission(context)) {
                        showPermissionDialog = true
                    } else {
                        proceedWithTrimAndSet(selectedTarget)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (selectedTarget) {
                        RingtoneTarget.PHONE_CALL -> "Set Call Ringtone"
                        RingtoneTarget.NOTIFICATION -> "Set Notification"
                        RingtoneTarget.ALARM -> "Set Alarm"
                        RingtoneTarget.SAVE_ONLY -> "Save Sound"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = {
                    stopPreview()
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

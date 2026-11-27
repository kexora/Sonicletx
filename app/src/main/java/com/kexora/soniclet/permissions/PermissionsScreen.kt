package com.kexora.soniclet.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Box-free, edge-to-edge Permissions & System Health Screen.
 */
@Composable
fun PermissionsSettingsSubPage() {
    val context = LocalContext.current

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val audioPermissionStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val videoPermissionStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember { mutableStateOf(isGranted(audioPermissionStr)) }
    var hasVideoPermission by remember { mutableStateOf(isGranted(videoPermissionStr)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                isGranted(android.Manifest.permission.POST_NOTIFICATIONS)
            else true
        )
    }
    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                isGranted(android.Manifest.permission.BLUETOOTH_CONNECT)
            else true
        )
    }
    var hasWriteSettingsPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.System.canWrite(context)
            else true
        )
    }

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            Toast.makeText(context, "MediaStore audio permission is required to play local songs.", Toast.LENGTH_SHORT).show()
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasVideoPermission = granted
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasBluetoothPermission = granted
    }

    val openSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    val openBatteryOptimization = {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            openSettings()
        }
    }

    val openWriteSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openSettings()
            }
        } else {
            openSettings()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = isGranted(audioPermissionStr)
                hasVideoPermission = isGranted(videoPermissionStr)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = isGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    hasBluetoothPermission = isGranted(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasWriteSettingsPermission = Settings.System.canWrite(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Borderless Emerald-Tinted Privacy Strip Banner
        EdgeInfoBanner(
            icon = Icons.Rounded.Security,
            title = "100% Offline & Privacy-First",
            message = "Soniclet processes your music locally on your device. Zero telemetry, no trackers, and your personal audio files never leave your phone."
        )

        // Section 1: Runtime Required Permissions
        EdgeSectionHeader(title = "RUNTIME ESSENTIALS")

        PermissionItemRow(
            icon = Icons.Rounded.AudioFile,
            iconTint = Color(0xFF00E676),
            title = "Music & Audio Storage",
            subtitle = "Discover, index, and play your offline music library, metadata, and album art",
            isGranted = hasAudioPermission,
            onClick = {
                if (!hasAudioPermission) {
                    audioLauncher.launch(audioPermissionStr)
                } else {
                    openSettings()
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EdgeHairlineDivider()
            PermissionItemRow(
                icon = Icons.Rounded.VideoLibrary,
                iconTint = Color(0xFFFF9100),
                title = "Video Library (Audio Extractor)",
                subtitle = "Used by the Video to M4A Converter to list local video files via MediaStore",
                isGranted = hasVideoPermission,
                onClick = {
                    if (!hasVideoPermission) {
                        videoLauncher.launch(videoPermissionStr)
                    } else {
                        openSettings()
                    }
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EdgeHairlineDivider()
            PermissionItemRow(
                icon = Icons.Rounded.NotificationsActive,
                iconTint = Color(0xFF00E5FF),
                title = "Media Notifications",
                subtitle = "Powers lockscreen controls, media notification bar, and sleep timer alerts",
                isGranted = hasNotificationPermission,
                onClick = {
                    if (!hasNotificationPermission) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openSettings()
                    }
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            EdgeHairlineDivider()
            PermissionItemRow(
                icon = Icons.Rounded.BluetoothAudio,
                iconTint = Color(0xFF3D5AFE),
                title = "Bluetooth & Headset Connection",
                subtitle = "Enables auto-resume on wireless devices and maps headphone button clicks",
                isGranted = hasBluetoothPermission,
                onClick = {
                    if (!hasBluetoothPermission) {
                        bluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        openSettings()
                    }
                }
            )
        }

        // Section 2: System Integrations & Health
        EdgeSectionHeader(title = "SYSTEM INTEGRATIONS & SETTINGS")

        PermissionItemRow(
            icon = Icons.Rounded.RingVolume,
            iconTint = Color(0xFFFF9100),
            title = "Modify System Settings (Ringtone)",
            subtitle = "Required to set any song or audio clip directly as your device ringtone",
            isGranted = hasWriteSettingsPermission,
            actionLabel = if (hasWriteSettingsPermission) "Allowed" else "Configure",
            onClick = {
                if (!hasWriteSettingsPermission) {
                    openWriteSettings()
                } else {
                    openSettings()
                }
            }
        )

        EdgeHairlineDivider()

        PermissionItemRow(
            icon = Icons.Rounded.BatteryChargingFull,
            iconTint = Color(0xFF00E676),
            title = "Unrestricted Battery Backgrounding",
            subtitle = "Prevents aggressive battery savers from interrupting background playback",
            isGranted = false,
            statusOverride = "Optimize",
            onClick = openBatteryOptimization
        )

        // Section 3: Automatic System Features
        EdgeSectionHeader(title = "SYSTEM DECLARED & SAFE")

        DeclaredItemRow(
            icon = Icons.Rounded.GraphicEq,
            title = "Foreground Media Service",
            description = "Ensures continuous studio-grade playback when screen is locked or minimized"
        )
        EdgeHairlineDivider()
        DeclaredItemRow(
            icon = Icons.Rounded.Public,
            title = "Online Metadata & Art Fetching",
            description = "Fetches high-resolution artist imagery, album artwork, and synced lyrics on demand"
        )
        EdgeHairlineDivider()
        DeclaredItemRow(
            icon = Icons.Rounded.PowerSettingsNew,
            title = "Restore Widget on Boot",
            description = "Re-initializes homescreen widgets and playback state when device restarts"
        )
        EdgeHairlineDivider()
        DeclaredItemRow(
            icon = Icons.Rounded.Tune,
            title = "Hardware Audio Processing",
            description = "Direct hardware DSP integration for Equalizer, Reverb, Bass Boost & 8D Audio"
        )

        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String? = null,
    statusOverride: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = if (isGranted) Color(0xFF00E676).copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            border = BorderStroke(
                0.8.dp,
                if (isGranted) Color(0xFF00E676).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGranted) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = statusOverride ?: actionLabel ?: if (isGranted) "Granted" else "Allow",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (isGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DeclaredItemRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

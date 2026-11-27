package com.kexora.soniclet.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Modern, balanced, and comprehensive Permissions & System Health Screen.
 * Provides clear information about runtime permissions, system integrations, and privacy.
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
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Privacy Banner
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "100% Offline & Privacy-First",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Soniclet processes your music locally on your device. Zero telemetry, no trackers, and your personal audio files never leave your phone.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Runtime Required Permissions
        PermissionSectionGroup(title = "RUNTIME ESSENTIALS") {
            PermissionItemRow(
                icon = Icons.Rounded.AudioFile,
                iconTint = Color(0xFF00E676),
                title = "Music & Audio Storage (MediaStore)",
                subtitle = "Required to discover, index, and play your offline music library, metadata, and album art.",
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
                PermissionDivider()
                PermissionItemRow(
                    icon = Icons.Rounded.VideoLibrary,
                    iconTint = Color(0xFFFF9100),
                    title = "Video Library (Audio Extractor)",
                    subtitle = "Used by the Video to M4A Audio Converter to list local video files via MediaStore.",
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
                PermissionDivider()
                PermissionItemRow(
                    icon = Icons.Rounded.NotificationsActive,
                    iconTint = Color(0xFF00E5FF),
                    title = "Media Notifications",
                    subtitle = "Powers lockscreen music controls, dynamic notification tiles, and sleep timer alerts.",
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
                PermissionDivider()
                PermissionItemRow(
                    icon = Icons.Rounded.BluetoothAudio,
                    iconTint = Color(0xFF3D5AFE),
                    title = "Bluetooth & Headset Connection",
                    subtitle = "Enables auto-resume on wireless devices and maps headphone hardware button clicks.",
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
        }

        // Section 2: System Integrations & Health
        PermissionSectionGroup(title = "SYSTEM INTEGRATIONS & SETTINGS") {
            PermissionItemRow(
                icon = Icons.Rounded.RingVolume,
                iconTint = Color(0xFFFF9100),
                title = "Modify System Settings (Ringtone)",
                subtitle = "Required if you want to set any song or audio clip directly as your device ringtone.",
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

            PermissionDivider()

            PermissionItemRow(
                icon = Icons.Rounded.BatteryChargingFull,
                iconTint = Color(0xFF00E676),
                title = "Unrestricted Battery Backgrounding",
                subtitle = "Prevents aggressive Android battery savers from terminating background music playback.",
                isGranted = false,
                statusOverride = "Optimize",
                onClick = openBatteryOptimization
            )
        }

        // Section 3: Automatic System Features
        PermissionSectionGroup(title = "SYSTEM DECLARED & SAFE") {
            DeclaredItemRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Foreground Media Service",
                description = "Ensures continuous studio-grade playback when screen is locked or app is minimized."
            )
            PermissionDivider()
            DeclaredItemRow(
                icon = Icons.Rounded.Public,
                title = "Online Metadata & Art Fetching",
                description = "Fetches high-resolution artist images, album artwork, and synchronized lyrics."
            )
            PermissionDivider()
            DeclaredItemRow(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Restore Widget on Boot",
                description = "Re-initializes homescreen widgets and playback state when device restarts."
            )
            PermissionDivider()
            DeclaredItemRow(
                icon = Icons.Rounded.Tune,
                title = "Hardware Audio Processing",
                description = "Direct hardware DSP integration for Equalizer, Reverb, Bass Boost & 8D Audio."
            )
        }

        MadeInIndiaFooter()
        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
    }
}

/**
 * Reusable clean grouped container for permissions
 */
@Composable
private fun PermissionSectionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                content = content
            )
        }
    }
}

/**
 * Interactive permission item row
 */
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = if (isGranted) Color(0xFF00E676).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (isGranted) Color(0xFF00E676).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                        fontSize = 10.5.sp
                    ),
                    color = if (isGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * System declared item row
 */
@Composable
private fun DeclaredItemRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Subtle divider between items
 */
@Composable
private fun PermissionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    )
}

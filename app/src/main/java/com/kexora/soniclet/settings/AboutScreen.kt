package com.kexora.soniclet.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kexora.soniclet.BuildConfig
import com.kexora.soniclet.R
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

private const val SONICLET_WEBSITE_URL = "https://soniclet.kexora.in"
private const val KEXORA_WEBSITE_URL = "https://kexora.in"
private const val SONICLET_PRIVACY_URL = "https://soniclet.kexora.in/privacy-policy"
private const val SONICLET_TERMS_URL = "https://soniclet.kexora.in/terms"
private const val SONICLET_INSTAGRAM_URL = "https://www.instagram.com/sonicletx"
private const val SONICLET_PRIMARY_EMAIL = "soniclet@kexora.in"
private const val SONICLET_FALLBACK_EMAIL = "support@kexora.in"

private val EmeraldBadgeColor = Color(0xFF10B981)

private fun isDeviceOnline(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        true
    }
}

private fun openUrlSafely(context: Context, url: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

private fun launchSupportEmail(
    context: Context,
    subject: String,
    body: String? = null
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SONICLET_PRIMARY_EMAIL")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SONICLET_PRIMARY_EMAIL, SONICLET_FALLBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (!body.isNullOrBlank()) {
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send Email"))
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Contact us at $SONICLET_PRIMARY_EMAIL",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Box-free, edge-to-edge About Soniclet Screen with borderless centered brand hero.
 * Preserves the 7-tap Muh Easter Egg, support intents, legal policies, and Kexora channels.
 */
@Composable
fun AboutSettingsSubPage(
    onOpenUserGuide: () -> Unit,
    isMohModeActivated: Boolean = false,
    onActivateMohMode: (Boolean) -> Unit = {},
    onOpenSpecialModeSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val mohPrefs = remember { context.getSharedPreferences("soniclet_preferences", Context.MODE_PRIVATE) }
    val mohPasscodes = remember {
        listOf(
            "muh",
            "bur",
            "meri muh sabse best hai",
            "meri bur sabse best hai",
            "meri muh ke malik ankit hai",
            "meri bur ke malik ankit hai",
            "karibhat ke muh ke malik ankit hai",
            "karibhat ke bur ke malik ankit hai",
            "karishama ke bur",
            "karishama ki bur",
            "karishama ke muh",
            "karishama ki muh"
        )
    }
    var passcodeLoopIndex by remember {
        mutableIntStateOf(mohPrefs.getInt("muh_passcode_loop_index", 0) % mohPasscodes.size)
    }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showMohSecretDialog by remember { mutableStateOf(false) }
    var showExitMohDialog by remember { mutableStateOf(false) }
    var showOfflinePrivacyDialog by remember { mutableStateOf(false) }
    var secretInputText by remember { mutableStateOf("") }
    var isInputError by remember { mutableStateOf(false) }
    var showLoveDiaryDialog by remember { mutableStateOf(false) }
    val specialDate = remember(isMohModeActivated) {
        if (isMohModeActivated) com.kexora.soniclet.easteregg.MuhLoveRegistry.getTodaySpecialDate() else null
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (isMohModeActivated) {
                com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.triggerImmediateCheck(context, forceDemo = true)
                Toast.makeText(context, "💌 Notification sent! Check your notification bar!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Notification permission is required for dedications! 🔔", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerLoveNotificationWithPermission = {
        if (isMohModeActivated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.triggerImmediateCheck(context, forceDemo = true)
                    Toast.makeText(context, "💌 Notification sent! Check your notification bar!", Toast.LENGTH_SHORT).show()
                }
            } else {
                com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.triggerImmediateCheck(context, forceDemo = true)
                Toast.makeText(context, "💌 Notification sent! Check your notification bar!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    if (showOfflinePrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showOfflinePrivacyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = EmeraldBadgeColor
                )
            },
            title = {
                Text(
                    text = "Privacy Policy Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Soniclet is designed as a 100% offline-first music player:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Your local music library, playlists, favorites, and Wrapped listening history stay strictly on your device.\n" +
                            "• Soniclet never uploads personal audio files or tracks you across apps.\n" +
                            "• Network access is used only when you explicitly turn on Online Radio or open external links.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Full policy: soniclet.kexora.in/privacy-policy",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showOfflinePrivacyDialog = false }) {
                    Text("Got It")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOfflinePrivacyDialog = false
                        if (!openUrlSafely(context, SONICLET_PRIVACY_URL)) {
                            Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Open Web")
                }
            }
        )
    }

    if (showMohSecretDialog) {
        AlertDialog(
            onDismissRequest = {
                showMohSecretDialog = false
                secretInputText = ""
                isInputError = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Secret Passcode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter secret key to unlock exclusive mode:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = secretInputText,
                        onValueChange = {
                            secretInputText = it
                            isInputError = false
                        },
                        singleLine = true,
                        placeholder = { Text("Secret code...") },
                        isError = isInputError,
                        supportingText = if (isInputError) {
                            { Text("गलत पासवर्ड! (इस बार पासवर्ड #${passcodeLoopIndex + 1} डालें) 💔") }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = secretInputText.trim()
                        val activeRequiredKey = mohPasscodes[passcodeLoopIndex]
                        val cleanInput = input.replace(" ", "").lowercase()
                        val cleanRequired = activeRequiredKey.replace(" ", "").lowercase()
                        val isMatch = input.equals(activeRequiredKey, ignoreCase = true) || cleanInput == cleanRequired

                        if (isMatch) {
                            onActivateMohMode(true)
                            val nextIndex = (passcodeLoopIndex + 1) % mohPasscodes.size
                            mohPrefs.edit().putInt("muh_passcode_loop_index", nextIndex).apply()
                            val usedNumber = passcodeLoopIndex + 1
                            passcodeLoopIndex = nextIndex

                            com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.syncSpecialCelebration(context)
                            triggerLoveNotificationWithPermission()
                            showMohSecretDialog = false
                            secretInputText = ""
                            isInputError = false
                            Toast.makeText(context, "💖 Mode Activated! (Passcode #$usedNumber used. Next cycle passcode: #$nextIndex) ✨", Toast.LENGTH_LONG).show()
                        } else {
                            isInputError = true
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMohSecretDialog = false
                        secretInputText = ""
                        isInputError = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExitMohDialog) {
        com.kexora.soniclet.ui.components.common.SonicletAlertDialog(
            onDismissRequest = { showExitMohDialog = false },
            title = "Exit Muh Edition?",
            message = "Kya aap Muh Edition / Karisma Edition ko band karke normal app mode me aana chahte hain? Aap bina app delete kiye kabhi bhi agle passcode ke sath ise wapas activate kar sakte hain.",
            confirmButtonText = "Exit Mode",
            onConfirm = {
                onActivateMohMode(false)
                com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(context)
                com.kexora.soniclet.worker.SpecialDateNotificationWorker.cancel(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                for (i in 0..20) {
                    nm?.cancel(com.kexora.soniclet.easteregg.MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
                }
                showExitMohDialog = false
                showLoveDiaryDialog = false
                Toast.makeText(context, "Muh Mode Deactivated 👋", Toast.LENGTH_SHORT).show()
            },
            dismissButtonText = "Cancel",
            onDismiss = { showExitMohDialog = false },
            icon = Icons.Rounded.PowerSettingsNew,
            isDestructive = true
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ==========================================
        // BORDERLESS CENTERED BRAND HERO HEADER
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 22.dp, start = 20.dp, end = 20.dp)
                .testTag("about_hero_card"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(logoScale)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary,
                        ambientColor = MaterialTheme.colorScheme.primary
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_soniclet_app_logo),
                        contentDescription = "Soniclet Logo",
                        modifier = Modifier.size(56.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Soniclet",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 27.sp,
                    letterSpacing = 0.4.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Pure Offline Hi-Res Music Experience",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Emerald Version Pill (7-tap Easter Egg trigger preserved)
            val pillAccent = if (isMohModeActivated) Color(0xFFFF4081) else MaterialTheme.colorScheme.primary
            Surface(
                onClick = {
                    if (isMohModeActivated) {
                        showExitMohDialog = true
                    } else {
                        versionTapCount++
                        if (versionTapCount >= 7) {
                            versionTapCount = 0
                            showMohSecretDialog = true
                        }
                    }
                },
                color = pillAccent.copy(alpha = 0.14f),
                shape = CircleShape,
                border = BorderStroke(1.dp, pillAccent.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("about_version_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(pillAccent)
                    )

                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        ),
                        color = pillAccent
                    )

                    if (isMohModeActivated) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFFF80AB)
                        )
                        Text(
                            text = "Muh Edition",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFFF4081)
                        )
                    }
                }
            }

            if (isMohModeActivated) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF1493).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💖 Karisma / Muh Edition Active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFFF80AB)
                        )

                        TextButton(
                            onClick = { showExitMohDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = "Exit Mode",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // Section 1: Discover & Support
        EdgeSectionHeader(title = "DISCOVER & SUPPORT")

        AboutItemRow(
            icon = Icons.Rounded.MenuBook,
            title = "User Guide & Gestures",
            subtitle = "Learn motion shortcuts, gestures, and audio engine features",
            tag = "about_user_guide",
            onClick = onOpenUserGuide
        )

        if (isMohModeActivated) {
            EdgeHairlineDivider()

            AboutItemRow(
                icon = Icons.Rounded.Favorite,
                title = "Special Mode & Celebration Dates",
                subtitle = "Manage special dates and preview the animated Special Edition splash screens",
                tag = "about_special_mode_settings",
                onClick = onOpenSpecialModeSettings
            )
        }

        EdgeHairlineDivider()

        AboutItemRow(
            icon = Icons.Rounded.BugReport,
            title = "Report an Issue",
            subtitle = "Send bug reports & ideas to $SONICLET_PRIMARY_EMAIL",
            tag = "about_report_issue",
            onClick = {
                launchSupportEmail(
                    context = context,
                    subject = "Soniclet v${BuildConfig.VERSION_NAME} Feedback / Support",
                    body = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Android: ${Build.VERSION.RELEASE}\n" +
                        "App Version: v${BuildConfig.VERSION_NAME}\n\n" +
                        "Issue / Feedback:\n"
                )
            }
        )

        EdgeHairlineDivider()

        AboutItemRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_playstore),
            title = "Rate on Play Store",
            subtitle = "Support Soniclet with a 5-star review",
            tag = "about_rate_play_store",
            onClick = {
                if (!openUrlSafely(context, "market://details?id=${context.packageName}")) {
                    if (!openUrlSafely(context, "https://play.google.com/store/apps/details?id=${context.packageName}")) {
                        Toast.makeText(context, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        EdgeHairlineDivider()

        AboutItemRow(
            icon = Icons.Rounded.Email,
            title = "Contact Developer",
            subtitle = "$SONICLET_PRIMARY_EMAIL • Kexora Support",
            tag = "about_contact_developer",
            onClick = {
                launchSupportEmail(
                    context = context,
                    subject = "Soniclet v${BuildConfig.VERSION_NAME} Feedback / Support"
                )
            }
        )

        // Section 2: Legal & Policies
        EdgeSectionHeader(title = "LEGAL & POLICIES")

        AboutItemRow(
            icon = Icons.Rounded.PrivacyTip,
            title = "Privacy Policy",
            subtitle = "100% offline, zero trackers — soniclet.kexora.in",
            tag = "about_privacy_policy",
            trailing = {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(17.dp)
                )
            },
            onClick = {
                if (!isDeviceOnline(context)) {
                    showOfflinePrivacyDialog = true
                } else if (!openUrlSafely(context, SONICLET_PRIVACY_URL)) {
                    showOfflinePrivacyDialog = true
                }
            }
        )

        EdgeHairlineDivider()

        AboutItemRow(
            icon = Icons.Rounded.Description,
            title = "Terms & Conditions",
            subtitle = "Usage guidelines, licenses & legal terms",
            tag = "about_terms",
            trailing = {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(17.dp)
                )
            },
            onClick = {
                if (!openUrlSafely(context, SONICLET_TERMS_URL)) {
                    Toast.makeText(context, "Unable to open soniclet.kexora.in/terms", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Section 3: Connect & Social Channels
        EdgeSectionHeader(title = "LET'S CONNECT")

        AboutSocialRow(
            icon = Icons.Rounded.Language,
            title = "Official Website",
            handle = "soniclet.kexora.in",
            tag = "about_website_soniclet",
            onClick = {
                if (!openUrlSafely(context, SONICLET_WEBSITE_URL)) {
                    Toast.makeText(context, "Unable to open website", Toast.LENGTH_SHORT).show()
                }
            }
        )

        EdgeHairlineDivider()

        AboutSocialRow(
            icon = Icons.Rounded.Public,
            title = "Kexora Studio",
            handle = "kexora.in",
            tag = "about_website_kexora",
            onClick = {
                if (!openUrlSafely(context, KEXORA_WEBSITE_URL)) {
                    Toast.makeText(context, "Unable to open website", Toast.LENGTH_SHORT).show()
                }
            }
        )

        EdgeHairlineDivider()

        AboutSocialRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_instagram),
            title = "Instagram",
            handle = "@sonicletx",
            tag = "about_instagram",
            onClick = {
                if (!openUrlSafely(context, SONICLET_INSTAGRAM_URL)) {
                    Toast.makeText(context, "Unable to open Instagram", Toast.LENGTH_SHORT).show()
                }
            }
        )

        EdgeHairlineDivider()

        AboutSocialRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_x),
            title = "Twitter / X",
            handle = "@kexora",
            tag = "about_twitter",
            onClick = {
                if (!openUrlSafely(context, "https://twitter.com/kexora")) {
                    Toast.makeText(context, "Unable to open X", Toast.LENGTH_SHORT).show()
                }
            }
        )

        EdgeHairlineDivider()

        AboutSocialRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_youtube),
            title = "YouTube",
            handle = "@kexora",
            tag = "about_youtube",
            onClick = {
                if (!openUrlSafely(context, "https://youtube.com/@kexora")) {
                    Toast.makeText(context, "Unable to open YouTube", Toast.LENGTH_SHORT).show()
                }
            }
        )

        EdgeHairlineDivider()

        AboutSocialRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_github),
            title = "GitHub",
            handle = "@kexora",
            tag = "about_github",
            onClick = {
                if (!openUrlSafely(context, "https://github.com/kexora")) {
                    Toast.makeText(context, "Unable to open GitHub", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Special Dedication & Tribute Section (Only visible when unlocked)
        if (isMohModeActivated && specialDate != null) {
            EdgeSectionHeader(title = "SPECIAL DEDICATION", color = Color(0xFFE91E63))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91E63).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Special Muse",
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "To Our Secret Muse",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\"Dedicated to the sweetest inspiration behind Soniclet — bringing that endless 'muh' smile and magical beats! ✨\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { showLoveDiaryDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Love Notes & Diary 📖💖", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onOpenSpecialModeSettings() },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage Special Dates & Preview Splash ✨", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { triggerLoveNotificationWithPermission() },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Test Love Notification 💖", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        if (isMohModeActivated && showLoveDiaryDialog) {
            com.kexora.soniclet.easteregg.MuhLoveDiaryDialog(
                dateInfo = specialDate,
                onDismiss = { showLoveDiaryDialog = false }
            )
        }

        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun AboutItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
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

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun AboutSocialRow(
    icon: ImageVector,
    title: String,
    handle: String,
    tag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = handle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

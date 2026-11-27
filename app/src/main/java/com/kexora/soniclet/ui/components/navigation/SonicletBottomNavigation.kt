package com.kexora.soniclet.ui.components.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.NavigationTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BottomTab(val tab: NavigationTab, val label: String, val icon: ImageVector) {
    PLAYER(NavigationTab.PLAYER, "Library", Icons.Default.LibraryMusic),
    PLAYLISTS(NavigationTab.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    STATS(NavigationTab.STATS, "Wrapped", Icons.Default.BarChart)
}

val defaultTabs = listOf(BottomTab.PLAYER, BottomTab.PLAYLISTS, BottomTab.STATS)

fun getTabOrder(context: Context): List<BottomTab> {
    val prefs = context.getSharedPreferences("soniclet_ui_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getString("bottom_tab_order", null)
    if (saved != null) {
        return try {
            val loaded = saved.split(",").map { BottomTab.valueOf(it) }
            if (loaded.size == BottomTab.values().size && loaded.containsAll(defaultTabs)) {
                loaded
            } else {
                defaultTabs
            }
        } catch (e: Exception) {
            defaultTabs
        }
    }
    return defaultTabs
}

fun saveTabOrder(context: Context, tabs: List<BottomTab>) {
    val prefs = context.getSharedPreferences("soniclet_ui_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("bottom_tab_order", tabs.joinToString(",") { it.name }).apply()
}

@Composable
fun SonicletBottomNavigation(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tabOrder by remember { mutableStateOf(getTabOrder(context)) }
    var showReorderDialog by remember { mutableStateOf(false) }

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        tabOrder.forEach { bottomTab ->
            NavigationBarItem(
                icon = { Icon(bottomTab.icon, contentDescription = bottomTab.label) },
                label = { Text(text = bottomTab.label) },
                selected = currentTab == bottomTab.tab,
                onClick = { onTabSelected(bottomTab.tab) },
                modifier = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val job = coroutineScope.launch {
                            delay(3000)
                            showReorderDialog = true
                        }
                        val up = waitForUpOrCancellation()
                        job.cancel()
                    }
                }
            )
        }
    }

    if (showReorderDialog) {
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("Customize Tab Order") },
            text = {
                Column {
                    Text("Reorder the bottom navigation tabs. Changes will be saved immediately.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    tabOrder.forEachIndexed { index, tab ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(tab.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tab.label, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = tabOrder.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index - 1]
                                        newList[index - 1] = temp
                                        tabOrder = newList
                                        saveTabOrder(context, newList)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                            }
                            
                            IconButton(
                                onClick = {
                                    if (index < tabOrder.size - 1) {
                                        val newList = tabOrder.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index + 1]
                                        newList[index + 1] = temp
                                        tabOrder = newList
                                        saveTabOrder(context, newList)
                                    }
                                },
                                enabled = index < tabOrder.size - 1
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReorderDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

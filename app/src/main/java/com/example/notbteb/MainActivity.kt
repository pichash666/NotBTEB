package com.example.notbteb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.notbteb.ui.theme.NotBTEBTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotBTEBTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
                
                var selectedCategory by remember { mutableStateOf(prefs.getString("selected_category", "All") ?: "All") }
                val notices = remember { mutableStateListOf<Notice>() }
                var lastUpdate by remember { mutableStateOf(prefs.getString("last_update", "") ?: "") }
                var isLoading by remember { mutableStateOf(false) }
                var isNotificationsEnabled by remember { 
                    mutableStateOf(prefs.getBoolean("notifications_enabled", false)) 
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        isNotificationsEnabled = true
                        prefs.edit().putBoolean("notifications_enabled", true).apply()
                        scheduleNotificationWorker(context)
                    }
                }

                LaunchedEffect(selectedCategory) {
                    isLoading = true
                    prefs.edit().putString("selected_category", selectedCategory).apply()
                    val response = NoticeScraper.fetchNotices(selectedCategory)
                    notices.clear()
                    notices.addAll(response.notices)
                    
                    if (response.lastUpdate.isNotEmpty()) {
                        lastUpdate = response.lastUpdate
                        prefs.edit().putString("last_update", lastUpdate).apply()
                    }
                    isLoading = false
                    
                    if (isNotificationsEnabled) {
                        scheduleNotificationWorker(context)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        TopControls(
                            selectedCategory = selectedCategory,
                            lastUpdate = lastUpdate,
                            notificationsEnabled = isNotificationsEnabled,
                            onCategorySelected = { selectedCategory = it },
                            onNotificationsChanged = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                            isNotificationsEnabled = true
                                            prefs.edit().putBoolean("notifications_enabled", true).apply()
                                            scheduleNotificationWorker(context)
                                        } else {
                                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        isNotificationsEnabled = true
                                        prefs.edit().putBoolean("notifications_enabled", true).apply()
                                        scheduleNotificationWorker(context)
                                    }
                                } else {
                                    isNotificationsEnabled = false
                                    prefs.edit().putBoolean("notifications_enabled", false).apply()
                                    cancelNotificationWorker(context)
                                }
                            }
                        )
                        if (isLoading) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            NoticeList(notices = notices)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleNotificationWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<NoticeWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "notice_monitor",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun cancelNotificationWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("notice_monitor")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopControls(
    selectedCategory: String,
    lastUpdate: String,
    notificationsEnabled: Boolean,
    onCategorySelected: (String) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit
) {
    val options = listOf("All", "Diploma", "SSC", "HSC")
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(250.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                value = selectedCategory,
                onValueChange = {},
                label = { Text("Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onCategorySelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { onNotificationsChanged(it) },
                thumbContent = {
                    Icon(
                        imageVector = if (notificationsEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
            if (lastUpdate.isNotEmpty()) {
                Text(
                    text = "Last: $lastUpdate",
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun NoticeList(notices: List<Notice>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(notices) { notice ->
            NoticeItem(notice)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun NoticeItem(notice: Notice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = notice.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = notice.date,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopControlsPreview() {
    NotBTEBTheme {
        TopControls(
            selectedCategory = "All",
            lastUpdate = "15-04-2026",
            notificationsEnabled = true,
            onCategorySelected = {},
            onNotificationsChanged = {}
        )
    }
}

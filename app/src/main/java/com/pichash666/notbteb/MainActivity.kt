package com.pichash666.notbteb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.pichash666.notbteb.ui.theme.NotBTEBTheme

class MainActivity : ComponentActivity() {
    private val viewModel: NoticeViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onResume() {
        super.onResume()
        viewModel.checkBatteryOptimizations()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotBTEBTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("app_prefs", MODE_PRIVATE) }
                
                val lastSyncTime by viewModel.lastSyncTime.collectAsState()
                
                var isNotificationsEnabled by remember { 
                    mutableStateOf(prefs.getBoolean("notifications_enabled", true))
                }

                val refreshState = rememberPullToRefreshState()

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    isNotificationsEnabled = isGranted
                    prefs.edit { putBoolean("notifications_enabled", isGranted) }
                    if (isGranted) {
                        NoticeWorker.schedule(context, immediate = true)
                        requestIgnoreBatteryOptimizations(context)
                    }
                }

                LaunchedEffect(Unit) {
                    if (isNotificationsEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NoticeWorker.schedule(context)
                                requestIgnoreBatteryOptimizations(context)
                            }
                        } else {
                            NoticeWorker.schedule(context)
                            requestIgnoreBatteryOptimizations(context)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("BTEB Notifier", fontWeight = FontWeight.Bold) },
                            actions = {
                                IconButton(onClick = { viewModel.refreshData() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                NotificationToggle(
                                    enabled = isNotificationsEnabled,
                                    onChanged = { enabled ->
                                        if (enabled) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                                    isNotificationsEnabled = true
                                                    prefs.edit { putBoolean("notifications_enabled", true) }
                                                    NoticeWorker.schedule(context, immediate = true)
                                                    requestIgnoreBatteryOptimizations(context)
                                                } else {
                                                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            } else {
                                                isNotificationsEnabled = true
                                                prefs.edit { putBoolean("notifications_enabled", true) }
                                                NoticeWorker.schedule(context, immediate = true)
                                                requestIgnoreBatteryOptimizations(context)
                                            }
                                        } else {
                                            isNotificationsEnabled = false
                                            prefs.edit { putBoolean("notifications_enabled", false) }
                                            NoticeWorker.cancel(context)
                                        }
                                    }
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        TabRow(selectedTabIndex = viewModel.selectedTabIndex) {
                            Tab(
                                selected = viewModel.selectedTabIndex == 0,
                                onClick = { viewModel.setTabIndex(0) },
                                text = { Text("Notices") }
                            )
                            Tab(
                                selected = viewModel.selectedTabIndex == 1,
                                onClick = { viewModel.setTabIndex(1) },
                                text = { Text("Results") }
                            )
                        }

                        if (viewModel.selectedTabIndex == 0) {
                            CategorySelector(
                                selectedCategory = viewModel.selectedCategory,
                                lastUpdate = viewModel.lastUpdate,
                                onCategorySelected = { viewModel.setCategory(it) }
                            )
                        } else {
                            if (viewModel.lastResultUpdate.isNotEmpty()) {
                                Text(
                                    text = "Last Updated: ${viewModel.lastResultUpdate}",
                                    modifier = Modifier.padding(16.dp, 8.dp),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (lastSyncTime > 0) {
                            val timeAgo = remember(lastSyncTime) {
                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(lastSyncTime))
                            }
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Background Sync: Active (Last: $timeAgo)",
                                    fontSize = 10.sp,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                                if (!viewModel.isIgnoringBatteryOptimizations && isNotificationsEnabled) {
                                    Text(
                                        text = "Optimization Active!",
                                        fontSize = 10.sp,
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { requestIgnoreBatteryOptimizations(this@MainActivity) }
                                    )
                                }
                            }
                        }

                        val currentList by remember(viewModel.selectedTabIndex) {
                            derivedStateOf { if (viewModel.selectedTabIndex == 0) viewModel.notices else viewModel.results }
                        }

                        PullToRefreshBox(
                            state = refreshState,
                            isRefreshing = viewModel.isLoading,
                            onRefresh = { viewModel.refreshData() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            NoticeList(notices = currentList)
                        }
                    }
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun NotificationToggle(
    enabled: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Switch(
        checked = enabled,
        onCheckedChange = { onChanged(it) },
        thumbContent = {
            Icon(
                imageVector = if (enabled) Icons.Filled.Notifications else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    selectedCategory: String,
    lastUpdate: String,
    onCategorySelected: (String) -> Unit
) {
    val options = remember { listOf("All", "Diploma", "SSC", "HSC") }
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
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                readOnly = true,
                value = selectedCategory,
                onValueChange = {},
                label = { Text("Category") },
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

        if (lastUpdate.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Update",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Text(
                    text = lastUpdate,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun NoticeList(notices: List<Notice>) {
    val uriHandler = LocalUriHandler.current
    if (notices.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No notices found", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(notices, key = { it.link }) { notice ->
                NoticeItem(
                    notice = notice,
                    onClick = {
                        if (notice.link.isNotEmpty()) {
                            uriHandler.openUri(notice.link)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun NoticeItem(notice: Notice, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = notice.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = notice.date,
                fontSize = 12.sp,
                color = Color.Gray
            )
            if (notice.link.isNotEmpty()) {
                Text(
                    text = "View PDF",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.telecom.CallManager
import com.example.telecom.CallUiState
import com.example.ui.screens.CallLogScreen
import com.example.ui.screens.ContactDetailScreen
import com.example.ui.screens.ContactEditScreen
import com.example.ui.screens.ContactsListScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.ContactsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ContactsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val currentTheme by viewModel.themeMode.collectAsState()
            val currentTab by viewModel.currentTab.collectAsState()
            val selectedContact by viewModel.selectedContact.collectAsState()
            val isEditingContact by viewModel.isEditingContact.collectAsState()
            val showDialpadOverlay by viewModel.showDialpadOverlay.collectAsState()
            val statusMessage by viewModel.statusMessage.collectAsState()
            val activeCall by CallManager.activeCallState.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(statusMessage) {
                statusMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearStatusMessage()
                }
            }

            MyApplicationTheme(themeMode = currentTheme) {
                when {
                    isEditingContact -> {
                        ContactEditScreen(
                            contact = selectedContact,
                            viewModel = viewModel,
                            onBack = { viewModel.cancelEdit() },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                        )
                    }
                    selectedContact != null -> {
                        ContactDetailScreen(
                            contact = selectedContact!!,
                            viewModel = viewModel,
                            onBack = { viewModel.selectContact(null) },
                            onEdit = { viewModel.startEditContact(selectedContact) },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                        )
                    }
                    showDialpadOverlay -> {
                        BackHandler { viewModel.closeDialpad() }
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            "لوحة الاتصال",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { viewModel.closeDialpad() }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "رجوع"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) { pad ->
                            DialerScreen(
                                viewModel = viewModel,
                                onAddContactWithNumber = { num ->
                                    viewModel.startEditContact(
                                        ContactItem(
                                            id = 0,
                                            displayName = "",
                                            primaryPhone = num,
                                            phoneNumbers = listOf(PhoneEntry("جوال", num))
                                        )
                                    )
                                },
                                onSelectContact = {
                                    viewModel.closeDialpad()
                                    viewModel.selectContact(it)
                                },
                                modifier = Modifier.padding(pad)
                            )
                        }
                    }
                    else -> {
                        BackHandler(enabled = currentTab != 0) {
                            viewModel.setCurrentTab(0)
                        }

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                LuxuryBottomNavigationBar(
                                    currentTab = currentTab,
                                    onTabSelected = { viewModel.setCurrentTab(it) }
                                )
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                // Active Call Floating Banner
                                AnimatedVisibility(
                                    visible = activeCall != null && activeCall?.callState != CallUiState.DISCONNECTED,
                                    enter = slideInVertically() + fadeIn(),
                                    exit = slideOutVertically() + fadeOut()
                                ) {
                                    val call = activeCall
                                    if (call != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SuccessEmerald)
                                                .clickable { CallManager.launchInCallScreen(this@MainActivity) }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${call.callerName.ifBlank { call.phoneNumber }} • ${call.callState.arabicStatus}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text(
                                                text = "العودة للمكالمة",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Crossfade(
                                    targetState = currentTab,
                                    label = "TabCrossfade",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) { tab ->
                                    when (tab) {
                                        0 -> {
                                            ContactsListScreen(
                                                viewModel = viewModel,
                                                onAddContactClick = { viewModel.startEditContact(null) },
                                                onContactClick = { viewModel.selectContact(it) }
                                            )
                                        }
                                        1 -> {
                                            CallLogScreen(
                                                viewModel = viewModel,
                                                onAddContactWithNumber = { num ->
                                                    viewModel.startEditContact(
                                                        ContactItem(
                                                            id = 0,
                                                            displayName = "",
                                                            primaryPhone = num,
                                                            phoneNumbers = listOf(PhoneEntry("جوال", num))
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                        else -> {
                                            SettingsScreen(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LuxuryBottomNavigationBar(
        currentTab: Int,
        onTabSelected: (Int) -> Unit
    ) {
        val tabs = listOf(
            LuxuryNavItem(0, "الأسماء", Icons.Filled.People, Icons.Outlined.People),
            LuxuryNavItem(1, "السجل", Icons.Filled.History, Icons.Outlined.History),
            LuxuryNavItem(2, "الإعدادات", Icons.Filled.Settings, Icons.Outlined.Settings)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 12.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                GoldPrimary.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { item ->
                        val isSelected = (currentTab == item.index)
                        val containerBg = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            Color.Transparent
                        }
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(containerBg)
                                .clickable { onTabSelected(item.index) }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .testTag("nav_tab_${item.index}"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                color = contentColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultDialerStatus()
        viewModel.refreshCallLogs()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data

        when (action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                if (data?.scheme == "tel") {
                    val phoneNumber = data.schemeSpecificPart ?: ""
                    viewModel.openDialpad(phoneNumber)
                } else if (action == Intent.ACTION_DIAL) {
                    viewModel.openDialpad()
                }
            }
            Intent.ACTION_CALL_BUTTON -> {
                viewModel.setCurrentTab(1) // Call Log tab
            }
            Intent.ACTION_INSERT -> {
                viewModel.startEditContact(null)
            }
        }
    }

    private data class LuxuryNavItem(
        val index: Int,
        val label: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
    )
}

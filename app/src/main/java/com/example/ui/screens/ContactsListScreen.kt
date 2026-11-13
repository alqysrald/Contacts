package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.ui.components.AlphabetIndexBar
import com.example.ui.components.ContactCard
import com.example.ui.components.DeveloperCreditsDialog
import com.example.ui.components.PinLockDialog
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.ContactsViewModel
import kotlinx.coroutines.launch

@Composable
fun ContactsListScreen(
    viewModel: ContactsViewModel,
    onAddContactClick: () -> Unit,
    onContactClick: (ContactItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contacts by viewModel.displayedContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDevDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Permission launcher for contacts
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        if (readGranted) {
            viewModel.syncSystemContacts()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("add_contact_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة جهة اتصال جديدة",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الأسماء Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${contacts.size} جهة اتصال مسجلة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Open Dialpad button
                    IconButton(
                        onClick = { viewModel.openDialpad() },
                        modifier = Modifier.testTag("open_dialpad_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = "لوحة الاتصال",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Sync with device
                    IconButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        },
                        modifier = Modifier.testTag("sync_contacts_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "مزامنة جهات اتصال الهاتف",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Vault lock status
                    IconButton(
                        onClick = {
                            if (isVaultUnlocked) {
                                viewModel.lockVault()
                            } else {
                                showPinDialog = true
                            }
                        },
                        modifier = Modifier.testTag("vault_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "الخزنة المشفرة",
                            tint = if (isVaultUnlocked) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Developer Dialog
                    IconButton(
                        onClick = { showDevDialog = true },
                        modifier = Modifier.testTag("dev_info_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "معلومات المطور",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Syncing indicator
            AnimatedVisibility(visible = isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("بحث بالاسم، الرقم، أو الوظيفة...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "مسح",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("contacts_search_input")
            )

            // Category Chips
            val categories = listOf("الكل", "المفضلة", "العائلة", "العمل", "الأصدقاء", "مهم جداً", "الخزنة المشفرة")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = (selectedCategory == cat)
                    val isVaultCat = (cat == "الخزنة المشفرة")

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isVaultCat && !isVaultUnlocked) {
                                showPinDialog = true
                            } else {
                                viewModel.setSelectedCategory(cat)
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isVaultCat) {
                                    Icon(
                                        imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else GoldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Contacts List with Alphabet side indexer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (contacts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedCategory == "الخزنة المشفرة" && !isVaultUnlocked)
                                    Icons.Default.Lock else Icons.Default.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedCategory == "الخزنة المشفرة" && !isVaultUnlocked)
                                    "الخزنة مقفلة. يرجى إدخال رمز PIN لعرض الأسماء المشفرة."
                                else if (searchQuery.isNotBlank())
                                    "لا توجد نتائج مطابقة للبحث"
                                else
                                    "لا توجد جهات اتصال محفوظة بعد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            if (selectedCategory == "الخزنة المشفرة" && !isVaultUnlocked) {
                                Button(onClick = { showPinDialog = true }) {
                                    Text("فتح الخزنة الآن", fontWeight = FontWeight.Bold)
                                }
                            } else if (searchQuery.isBlank() && selectedCategory == "الكل") {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.WRITE_CONTACTS,
                                                Manifest.permission.CALL_PHONE
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("مزامنة جهات اتصال الهاتف", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onAddContactClick,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إضافة جهة اتصال يدوياً")
                                }
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = contacts,
                                key = { it.id },
                                contentType = { "contact_item" }
                            ) { contact ->
                                ContactCard(
                                    contact = contact,
                                    onClick = { onContactClick(contact) },
                                    onCallClick = { viewModel.makePhoneCall(contact.primaryPhone) },
                                    onWhatsAppClick = { viewModel.openWhatsApp(contact.primaryPhone) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(contact.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }

                        // Alphabet Quick Index
                        AlphabetIndexBar(
                            onLetterSelected = { letter ->
                                val targetIndex = contacts.indexOfFirst {
                                    it.displayName.trim().startsWith(letter, ignoreCase = true)
                                }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(targetIndex)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // PIN dialog
    PinLockDialog(
        isOpen = showPinDialog,
        onDismiss = { showPinDialog = false },
        onPinEntered = { pin ->
            val unlocked = viewModel.unlockVault(pin)
            if (unlocked) {
                showPinDialog = false
                viewModel.setSelectedCategory("الخزنة المشفرة")
            }
            unlocked
        }
    )

    // Developer credits dialog
    DeveloperCreditsDialog(
        isOpen = showDevDialog,
        onDismiss = { showDevDialog = false },
        onWhatsAppClick = { viewModel.contactDeveloperWhatsApp() },
        onCallClick = { viewModel.callDeveloper() }
    )
}

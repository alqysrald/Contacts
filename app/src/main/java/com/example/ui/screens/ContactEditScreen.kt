package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.data.vcard.VCardManager
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    contact: ContactItem?,
    viewModel: ContactsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val isNew = (contact == null)
    var name by remember { mutableStateOf(contact?.displayName ?: "") }
    var organization by remember { mutableStateOf(contact?.organization ?: "") }
    var jobTitle by remember { mutableStateOf(contact?.jobTitle ?: "") }
    var selectedCategory by remember { mutableStateOf(contact?.category ?: "عام") }
    var birthday by remember { mutableStateOf(contact?.birthday ?: "") }
    var notes by remember { mutableStateOf(contact?.notes ?: "") }
    var address by remember { mutableStateOf(contact?.address ?: "") }
    var isVault by remember { mutableStateOf(contact?.isVault ?: false) }

    // Phones list
    val phonesList = remember {
        mutableStateListOf<PhoneEntry>().apply {
            if (contact != null && contact.phoneNumbers.isNotEmpty()) {
                addAll(contact.phoneNumbers)
            } else if (contact != null && contact.primaryPhone.isNotBlank()) {
                add(PhoneEntry("جوال", contact.primaryPhone))
            } else {
                add(PhoneEntry("جوال", ""))
            }
        }
    }

    // Emails list
    val emailsList = remember {
        mutableStateListOf<String>().apply {
            if (contact != null && contact.emails.isNotEmpty()) {
                addAll(contact.emails)
            } else {
                add("")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "إضافة جهة اتصال جديدة" else "تعديل جهة الاتصال") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "إلغاء"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val cleanPhones = phonesList.filter { it.number.isNotBlank() }
                            val cleanEmails = emailsList.filter { it.isNotBlank() }
                            val primary = cleanPhones.firstOrNull()?.number ?: ""

                            val itemToSave = ContactItem(
                                id = contact?.id ?: 0L,
                                systemContactId = contact?.systemContactId,
                                displayName = name.ifBlank { primary.ifBlank { "جهة اتصال جديدة" } },
                                primaryPhone = primary,
                                phoneNumbers = cleanPhones,
                                emails = cleanEmails,
                                organization = organization,
                                jobTitle = jobTitle,
                                category = if (isVault) "الخزنة المشفرة" else selectedCategory,
                                isFavorite = contact?.isFavorite ?: false,
                                isVault = isVault,
                                birthday = birthday.takeIf { it.isNotBlank() },
                                notes = notes,
                                address = address,
                                avatarColorHex = contact?.avatarColorHex ?: VCardManager.getRandomPaletteColor(name)
                            )
                            viewModel.saveContact(itemToSave)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_contact_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("الاسم الكامل *") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_name_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone Numbers Dynamic Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أرقام الهواتف",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = { phonesList.add(PhoneEntry("جوال", "")) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة رقم")
                        }
                    }

                    phonesList.forEachIndexed { index, phoneEntry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = phoneEntry.number,
                                onValueChange = { newNum ->
                                    phonesList[index] = phoneEntry.copy(number = newNum)
                                },
                                label = { Text("رقم الهاتف ${index + 1}") },
                                leadingIcon = {
                                    Icon(Icons.Default.Call, contentDescription = null)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_phone_input_$index")
                            )

                            if (phonesList.size > 1) {
                                IconButton(onClick = { phonesList.removeAt(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الرقم",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selector
            Text(
                text = "التصنيف / المجموعة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val categories = listOf("عام", "العائلة", "العمل", "الأصدقاء", "مهم جداً")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = (selectedCategory == cat),
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Work & Organization
            OutlinedTextField(
                value = organization,
                onValueChange = { organization = it },
                label = { Text("الشركة / جهة العمل") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = jobTitle,
                onValueChange = { jobTitle = it },
                label = { Text("المسمى الوظيفي") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Birthday & Occasions
            OutlinedTextField(
                value = birthday,
                onValueChange = { birthday = it },
                label = { Text("تاريخ الميلاد أو المناسبة (مثال: 1995-10-25 أو 10-25)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        tint = GoldPrimary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Email
            OutlinedTextField(
                value = emailsList.firstOrNull() ?: "",
                onValueChange = {
                    if (emailsList.isEmpty()) emailsList.add(it) else emailsList[0] = it
                },
                label = { Text("البريد الإلكتروني") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Address
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("العنوان / المدينة") },
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Vault Toggle Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isVault) GoldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "حماية الخزنة المشفرة 🔒",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "إخفاء جهة الاتصال وتشفير بياناتها برمز الأمان",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isVault,
                        onCheckedChange = { isVault = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات خاصة (يتم تشفيرها تلقائياً بتقنية AES-256)") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

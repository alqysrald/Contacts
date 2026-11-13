package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.ContactsViewModel

@Composable
fun DialerScreen(
    viewModel: ContactsViewModel,
    onAddContactWithNumber: (String) -> Unit,
    onSelectContact: (ContactItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dialerNumber by viewModel.dialerNumber.collectAsState()
    val allContacts by viewModel.displayedContacts.collectAsState()

    // Matching contacts
    val matchedContacts = if (dialerNumber.isNotBlank()) {
        val cleanDigits = dialerNumber.replace("[^0-9+]".toRegex(), "")
        allContacts.filter { c ->
            c.primaryPhone.contains(cleanDigits) || c.phoneNumbers.any { it.number.contains(cleanDigits) }
        }.take(3)
    } else emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Matched Contacts preview
        AnimatedVisibility(visible = matchedContacts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                matchedContacts.forEach { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onSelectContact(contact) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = contact.primaryPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.makePhoneCall(contact.primaryPhone) }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "اتصال",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Dialed Number Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (dialerNumber.isEmpty()) "لوحة الاتصال" else dialerNumber,
                style = if (dialerNumber.isEmpty()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (dialerNumber.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Add to Contacts Action
        AnimatedVisibility(visible = dialerNumber.isNotBlank()) {
            TextButton(
                onClick = { onAddContactWithNumber(dialerNumber) },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "إضافة إلى جهات الاتصال",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Dial Pad Grid
        val keys = listOf(
            Triple("1", "", '1'),
            Triple("2", "ABC", '2'),
            Triple("3", "DEF", '3'),
            Triple("4", "GHI", '4'),
            Triple("5", "JKL", '5'),
            Triple("6", "MNO", '6'),
            Triple("7", "PQRS", '7'),
            Triple("8", "TUV", '8'),
            Triple("9", "WXYZ", '9'),
            Triple("*", "", '*'),
            Triple("0", "+", '0'),
            Triple("#", "", '#')
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 3) {
                        val item = keys[row * 3 + col]
                        DialKeyButton(
                            primary = item.first,
                            secondary = item.second,
                            onClick = { viewModel.appendToDialer(item.third) },
                            onLongClick = {
                                if (item.first == "0") {
                                    viewModel.appendToDialer('+')
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Actions: WhatsApp, Call, Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WhatsApp chat button
            IconButton(
                onClick = {
                    if (dialerNumber.isNotBlank()) {
                        viewModel.openWhatsApp(dialerNumber)
                    }
                },
                enabled = dialerNumber.isNotBlank(),
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (dialerNumber.isNotBlank()) WhatsAppGreen.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Text(
                    text = "WA",
                    color = if (dialerNumber.isNotBlank()) WhatsAppGreen else Color.Transparent,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }

            // Big Green Call Button
            FloatingActionButton(
                onClick = {
                    if (dialerNumber.isNotBlank()) {
                        viewModel.makePhoneCall(dialerNumber)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("dialer_call_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "اتصال هاتفي",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Backspace / Clear button
            IconButton(
                onClick = { viewModel.backspaceDialer() },
                enabled = dialerNumber.isNotEmpty(),
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "حذف الرقم",
                    tint = if (dialerNumber.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
fun DialKeyButton(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

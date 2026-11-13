package com.example.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.telecom.ActiveCallUiState
import com.example.telecom.AudioRouteOption
import com.example.telecom.CallManager
import com.example.telecom.CallUiState
import com.example.ui.theme.AlertRuby
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenFlags()

        setContent {
            val callState by CallManager.activeCallState.collectAsState()

            LaunchedEffect(callState) {
                if (callState == null) {
                    finish()
                }
            }

            MyApplicationTheme(themeMode = AppThemeMode.ROYAL_GOLD) {
                val active = callState
                if (active != null) {
                    InCallScreen(
                        state = active,
                        onAnswer = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                km?.requestDismissKeyguard(this, null)
                            }
                            CallManager.answerCall()
                        },
                        onReject = { msg -> CallManager.rejectCall(msg) },
                        onDisconnect = { CallManager.disconnectCall() },
                        onToggleMute = { CallManager.toggleMute() },
                        onToggleSpeaker = { CallManager.toggleSpeaker() },
                        onCycleBluetooth = {
                            val next = if (active.audioRoute == AudioRouteOption.BLUETOOTH) {
                                AudioRouteOption.EARPIECE
                            } else {
                                AudioRouteOption.BLUETOOTH
                            }
                            CallManager.setAudioRoute(next)
                        },
                        onToggleHold = { CallManager.toggleHold() },
                        onDtmfPress = { digit ->
                            CallManager.playDtmf(digit)
                        },
                        onDtmfRelease = { CallManager.stopDtmf() },
                        onOpenContacts = {
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                }
                            )
                        },
                        onOpenWhatsApp = {
                            val clean = active.phoneNumber.replace("[^0-9+]".toRegex(), "")
                            val intl = if (clean.startsWith("+")) clean.drop(1)
                            else if (clean.startsWith("7") && clean.length == 9) "967$clean"
                            else clean
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun InCallScreen(
    state: ActiveCallUiState,
    onAnswer: () -> Unit,
    onReject: (String?) -> Unit,
    onDisconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onCycleBluetooth: () -> Unit,
    onToggleHold: () -> Unit,
    onDtmfPress: (Char) -> Unit,
    onDtmfRelease: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var dtmfSequence by remember { mutableStateOf("") }
    var showRejectSmsDialog by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.callState, state.connectedAtMillis) {
        if (state.callState == CallUiState.ACTIVE && state.connectedAtMillis > 0L) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - state.connectedAtMillis) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "callPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val displayName = state.callerName.ifBlank { state.phoneNumber.ifBlank { "مكالمة هاتفية" } }
    val initialChar = displayName.trim().firstOrNull()?.uppercase() ?: "؟"
    val showNumberSubtitle = state.callerName.isNotBlank() && state.phoneNumber.isNotBlank()

    val isRinging = state.callState == CallUiState.RINGING
    val isPulsing = isRinging || state.callState == CallUiState.DIALING || state.callState == CallUiState.CONNECTING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060A13),
                        Color(0xFF0E1729),
                        Color(0xFF151C2C),
                        Color(0xFF070B14)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = when (state.callState) {
                        CallUiState.ACTIVE -> SuccessEmerald.copy(alpha = 0.18f)
                        CallUiState.DISCONNECTED -> AlertRuby.copy(alpha = 0.2f)
                        else -> GoldPrimary.copy(alpha = 0.16f)
                    },
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = when (state.callState) {
                            CallUiState.ACTIVE -> SuccessEmerald.copy(alpha = 0.45f)
                            CallUiState.DISCONNECTED -> AlertRuby.copy(alpha = 0.45f)
                            else -> GoldPrimary.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(24.dp)
                    )
                ) {
                    Text(
                        text = if (state.callState == CallUiState.ACTIVE) {
                            "${state.callState.arabicStatus} • ${formatCallDuration(elapsedSeconds)}"
                        } else if (state.callState == CallUiState.DISCONNECTED && state.disconnectReason.isNotBlank()) {
                            state.disconnectReason
                        } else {
                            state.callState.arabicStatus
                        },
                        color = when (state.callState) {
                            CallUiState.ACTIVE -> SuccessEmerald
                            CallUiState.DISCONNECTED -> AlertRuby
                            else -> GoldLight
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Avatar with animated golden aura
                Box(contentAlignment = Alignment.Center) {
                    if (isPulsing) {
                        Box(
                            modifier = Modifier
                                .size(134.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.14f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(114.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(GoldLight, GoldPrimary, Color(0xFF7D5F0B))
                                )
                            )
                            .border(2.dp, GoldLight.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialChar,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF140F00)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (showNumberSubtitle) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldLight.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Middle: In-Call DTMF Keypad OR Glassmorphic Controls Grid
            if (!isRinging && state.callState != CallUiState.DISCONNECTED) {
                AnimatedVisibility(
                    visible = showKeypad,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    InCallDtmfKeypad(
                        enteredDigits = dtmfSequence,
                        onDigitClick = { ch ->
                            dtmfSequence += ch
                            onDtmfPress(ch)
                        },
                        onDigitRelease = onDtmfRelease,
                        onClose = { showKeypad = false }
                    )
                }

                AnimatedVisibility(
                    visible = !showKeypad,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallControlButton(
                                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (state.isMuted) "إلغاء الكتم" else "كتم الصوت",
                                isActive = state.isMuted,
                                onClick = onToggleMute
                            )
                            InCallControlButton(
                                icon = Icons.Default.Dialpad,
                                label = "لوحة الأرقام",
                                isActive = showKeypad,
                                onClick = { showKeypad = true }
                            )
                            InCallControlButton(
                                icon = if (state.audioRoute == AudioRouteOption.BLUETOOTH) Icons.Default.BluetoothAudio else Icons.Default.VolumeUp,
                                label = if (state.audioRoute == AudioRouteOption.SPEAKER) "مكبر الصوت" else state.audioRoute.arabicLabel,
                                isActive = state.audioRoute == AudioRouteOption.SPEAKER || state.audioRoute == AudioRouteOption.BLUETOOTH,
                                onClick = {
                                    if (state.isBluetoothAvailable) {
                                        onCycleBluetooth()
                                    } else {
                                        onToggleSpeaker()
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallControlButton(
                                icon = if (state.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (state.isOnHold) "استئناف" else "تعليق",
                                isActive = state.isOnHold,
                                onClick = onToggleHold
                            )
                            InCallControlButton(
                                icon = Icons.Default.People,
                                label = "الأسماء",
                                isActive = false,
                                onClick = onOpenContacts
                            )
                            InCallControlButton(
                                iconText = "WA",
                                label = "واتساب",
                                isActive = false,
                                tint = WhatsAppGreen,
                                onClick = onOpenWhatsApp
                            )
                        }
                    }
                }
            }

            // Bottom Action Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                if (isRinging) {
                    // Reject with SMS pill
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .clickable { showRejectSmsDialog = true }
                            .padding(bottom = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Message,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "رد برسالة سريعة",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Answer / Decline row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = { onReject(null) },
                                containerColor = AlertRuby,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("incall_decline_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "رفض المكالمة",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("رفض", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onAnswer,
                                containerColor = SuccessEmerald,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(pulseScale)
                                    .testTag("incall_answer_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "رد على المكالمة",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("رد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Hangup Button
                    FloatingActionButton(
                        onClick = onDisconnect,
                        containerColor = AlertRuby,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(74.dp)
                            .testTag("incall_hangup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "إنهاء المكالمة",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }

    if (showRejectSmsDialog) {
        val quickReplies = listOf(
            "أنا مشغول الآن، سأتصل بك لاحقاً.",
            "أنا في اجتماع حالياً، سأعاود الاتصال قريباً.",
            "أنا أقود السيارة، سأتصل بك فور وصولي."
        )
        AlertDialog(
            onDismissRequest = { showRejectSmsDialog = false },
            title = { Text("رفض مع إرسال رسالة سريعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickReplies.forEach { msg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRejectSmsDialog = false
                                    onReject(msg)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRejectSmsDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun InCallControlButton(
    icon: ImageVector? = null,
    iconText: String? = null,
    label: String,
    isActive: Boolean,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) GoldPrimary else Color.White.copy(alpha = 0.1f)
                )
                .border(
                    width = 1.dp,
                    color = if (isActive) GoldLight else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else tint,
                    modifier = Modifier.size(26.dp)
                )
            } else if (iconText != null) {
                Text(
                    text = iconText,
                    color = if (isActive) Color.Black else tint,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun InCallDtmfKeypad(
    enteredDigits: String,
    onDigitClick: (Char) -> Unit,
    onDigitRelease: () -> Unit,
    onClose: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11192A).copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = enteredDigits.ifEmpty { "لوحة الأرقام (DTMF)" },
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { digit ->
                        Surface(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onDigitClick(digit)
                                    onDigitRelease()
                                },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit.toString(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatCallDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}

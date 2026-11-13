package com.example.telecom

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.example.data.local.AppDatabase
import com.example.data.model.CallDirection
import com.example.data.repository.CallLogRepository
import com.example.ui.call.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallUiState(val arabicStatus: String) {
    IDLE("لا توجد مكالمة"),
    DIALING("جارٍ الاتصال..."),
    RINGING("مكالمة واردة"),
    CONNECTING("جارٍ الربط..."),
    ACTIVE("مكالمة جارية"),
    HOLDING("المكالمة قيد الانتظار"),
    DISCONNECTED("انتهت المكالمة")
}

enum class AudioRouteOption(val arabicLabel: String) {
    EARPIECE("سماعة الهاتف"),
    SPEAKER("مكبر الصوت"),
    BLUETOOTH("بلوتوث"),
    WIRED_HEADSET("سماعة سلكية")
}

data class ActiveCallUiState(
    val phoneNumber: String = "",
    val callerName: String = "",
    val callState: CallUiState = CallUiState.IDLE,
    val isIncoming: Boolean = false,
    val isMuted: Boolean = false,
    val audioRoute: AudioRouteOption = AudioRouteOption.EARPIECE,
    val isBluetoothAvailable: Boolean = false,
    val isOnHold: Boolean = false,
    val connectedAtMillis: Long = 0L,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val disconnectReason: String = ""
)

object CallManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeCallState = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallState: StateFlow<ActiveCallUiState?> = _activeCallState.asStateFlow()

    private var currentCall: Call? = null
    private var inCallService: InCallService? = null
    private var appContext: Context? = null
    private var hasBeenAnswered = false

    // API 34+ endpoint tracking
    private var availableEndpointsList: List<Any> = emptyList()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            refreshCallerInfo(call)
        }
    }

    fun attachService(service: InCallService) {
        inCallService = service
        appContext = service.applicationContext
    }

    fun detachService(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
        }
    }

    fun onCallAdded(call: Call, context: Context) {
        appContext = context.applicationContext
        currentCall = call
        hasBeenAnswered = false
        call.registerCallback(callCallback)

        val number = extractPhoneNumber(call)
        val initialName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: call.details?.contactDisplayName?.takeIf { it.isNotBlank() }
            ?: ""

        val initialState = mapTelecomState(getCallStateCompat(call))
        val isIncoming = initialState == CallUiState.RINGING

        _activeCallState.value = ActiveCallUiState(
            phoneNumber = number,
            callerName = initialName,
            callState = initialState,
            isIncoming = isIncoming,
            startedAtMillis = System.currentTimeMillis()
        )

        // Resolve saved contact name asynchronously
        scope.launch {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val repo = CallLogRepository(context.applicationContext, db.callLogDao(), db.contactDao())
            val resolved = repo.resolveContactName(number)
            if (!resolved.isNullOrBlank()) {
                _activeCallState.value = _activeCallState.value?.copy(callerName = resolved)
                val currentUi = _activeCallState.value
                if (currentUi != null) {
                    if (currentUi.callState == CallUiState.RINGING) {
                        CallNotificationManager.showIncomingCallNotification(context, resolved, number)
                    } else if (currentUi.callState != CallUiState.DISCONNECTED) {
                        CallNotificationManager.showOngoingCallNotification(
                            context,
                            resolved,
                            number,
                            currentUi.callState.arabicStatus
                        )
                    }
                }
            }
        }

        if (isIncoming) {
            CallNotificationManager.showIncomingCallNotification(context, initialName, number)
        } else {
            CallNotificationManager.showOngoingCallNotification(
                context,
                initialName,
                number,
                initialState.arabicStatus
            )
        }

        launchInCallScreen(context)
    }

    fun onCallRemoved(call: Call, context: Context) {
        call.unregisterCallback(callCallback)
        if (currentCall === call) {
            val prevState = _activeCallState.value
            if (prevState != null && prevState.callState != CallUiState.DISCONNECTED) {
                finalizeAndLogCall(call, context, prevState)
            }
            CallNotificationManager.cancelCallNotification(context)
            currentCall = null
            // Keep DISCONNECTED state briefly for UI fade-out, then clear
            scope.launch {
                kotlinx.coroutines.delay(1400)
                if (currentCall == null) {
                    _activeCallState.value = null
                }
            }
        }
    }

    private fun updateCallState(call: Call, telecomState: Int) {
        val mapped = mapTelecomState(telecomState)
        val current = _activeCallState.value ?: return
        val ctx = appContext

        val connectedAt = if (mapped == CallUiState.ACTIVE && current.connectedAtMillis == 0L) {
            hasBeenAnswered = true
            System.currentTimeMillis()
        } else {
            current.connectedAtMillis
        }

        val disconnectReason = if (mapped == CallUiState.DISCONNECTED) {
            val label = call.details?.disconnectCause?.label?.toString()
            label?.takeIf { it.isNotBlank() } ?: "انتهت المكالمة"
        } else ""

        val updated = current.copy(
            callState = mapped,
            isOnHold = (mapped == CallUiState.HOLDING),
            connectedAtMillis = connectedAt,
            disconnectReason = disconnectReason
        )
        _activeCallState.value = updated

        if (ctx != null) {
            when (mapped) {
                CallUiState.RINGING -> {
                    CallNotificationManager.showIncomingCallNotification(ctx, updated.callerName, updated.phoneNumber)
                }
                CallUiState.ACTIVE, CallUiState.DIALING, CallUiState.CONNECTING, CallUiState.HOLDING -> {
                    CallNotificationManager.showOngoingCallNotification(
                        ctx,
                        updated.callerName,
                        updated.phoneNumber,
                        mapped.arabicStatus
                    )
                }
                CallUiState.DISCONNECTED -> {
                    finalizeAndLogCall(call, ctx, updated)
                    CallNotificationManager.cancelCallNotification(ctx)
                }
                else -> {}
            }
        }
    }

    private fun finalizeAndLogCall(call: Call, context: Context, state: ActiveCallUiState) {
        val durationSec = if (state.connectedAtMillis > 0L) {
            ((System.currentTimeMillis() - state.connectedAtMillis) / 1000L).coerceAtLeast(1L)
        } else 0L

        val causeCode = call.details?.disconnectCause?.code
        val direction = when {
            !state.isIncoming -> CallDirection.OUTGOING
            hasBeenAnswered || state.connectedAtMillis > 0L -> CallDirection.INCOMING
            causeCode == DisconnectCause.REJECTED -> CallDirection.REJECTED
            causeCode == DisconnectCause.MISSED -> CallDirection.MISSED
            else -> CallDirection.MISSED
        }

        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val repo = CallLogRepository(context.applicationContext, db.callLogDao(), db.contactDao())
            repo.recordLocalCall(
                number = state.phoneNumber,
                cachedName = state.callerName,
                direction = direction,
                timestamp = state.startedAtMillis,
                durationSeconds = durationSec
            )
        }
    }

    private fun refreshCallerInfo(call: Call) {
        val current = _activeCallState.value ?: return
        val num = extractPhoneNumber(call)
        val detailsName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: call.details?.contactDisplayName?.takeIf { it.isNotBlank() }
        _activeCallState.value = current.copy(
            phoneNumber = num.ifBlank { current.phoneNumber },
            callerName = detailsName ?: current.callerName
        )
    }

    @Suppress("DEPRECATION")
    fun updateAudioState(audioState: CallAudioState) {
        val current = _activeCallState.value ?: return
        val route = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRouteOption.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRouteOption.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRouteOption.WIRED_HEADSET
            else -> AudioRouteOption.EARPIECE
        }
        val btSupported = (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        _activeCallState.value = current.copy(
            isMuted = audioState.isMuted,
            audioRoute = route,
            isBluetoothAvailable = btSupported
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun updateCallEndpoint(endpoint: CallEndpoint) {
        val current = _activeCallState.value ?: return
        val route = when (endpoint.endpointType) {
            CallEndpoint.TYPE_SPEAKER -> AudioRouteOption.SPEAKER
            CallEndpoint.TYPE_BLUETOOTH -> AudioRouteOption.BLUETOOTH
            CallEndpoint.TYPE_WIRED_HEADSET -> AudioRouteOption.WIRED_HEADSET
            else -> AudioRouteOption.EARPIECE
        }
        _activeCallState.value = current.copy(audioRoute = route)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun updateAvailableEndpoints(endpoints: List<CallEndpoint>) {
        availableEndpointsList = endpoints
        val current = _activeCallState.value ?: return
        val btSupported = endpoints.any { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        _activeCallState.value = current.copy(isBluetoothAvailable = btSupported)
    }

    fun updateMuteState(muted: Boolean) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(isMuted = muted)
    }

    fun answerCall() {
        try {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            hasBeenAnswered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rejectCall(quickSmsMessage: String? = null) {
        try {
            if (quickSmsMessage != null) {
                currentCall?.reject(true, quickSmsMessage)
            } else {
                currentCall?.reject(false, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnectCall() {
        try {
            val call = currentCall ?: return
            if (getCallStateCompat(call) == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleHold() {
        try {
            val call = currentCall ?: return
            if (_activeCallState.value?.isOnHold == true) {
                call.unhold()
            } else {
                call.hold()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleMute() {
        val current = _activeCallState.value ?: return
        val nextMuted = !current.isMuted
        try {
            inCallService?.setMuted(nextMuted)
            val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMicrophoneMute = nextMuted
            _activeCallState.value = current.copy(isMuted = nextMuted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleSpeaker() {
        val current = _activeCallState.value ?: return
        val target = if (current.audioRoute == AudioRouteOption.SPEAKER) {
            AudioRouteOption.EARPIECE
        } else {
            AudioRouteOption.SPEAKER
        }
        setAudioRoute(target)
    }

    fun setAudioRoute(option: AudioRouteOption) {
        val current = _activeCallState.value ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val targetType = when (option) {
                    AudioRouteOption.SPEAKER -> CallEndpoint.TYPE_SPEAKER
                    AudioRouteOption.BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
                    AudioRouteOption.WIRED_HEADSET -> CallEndpoint.TYPE_WIRED_HEADSET
                    AudioRouteOption.EARPIECE -> CallEndpoint.TYPE_EARPIECE
                }
                val endpoint = availableEndpointsList
                    .filterIsInstance<CallEndpoint>()
                    .firstOrNull { it.endpointType == targetType }
                if (endpoint != null && appContext != null) {
                    inCallService?.requestCallEndpointChange(
                        endpoint,
                        appContext!!.mainExecutor
                    ) { /* outcome ignored */ }
                }
            } else {
                @Suppress("DEPRECATION")
                val routeBit = when (option) {
                    AudioRouteOption.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                    AudioRouteOption.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                    AudioRouteOption.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                    AudioRouteOption.EARPIECE -> CallAudioState.ROUTE_EARPIECE
                }
                @Suppress("DEPRECATION")
                inCallService?.setAudioRoute(routeBit)
            }
            _activeCallState.value = current.copy(audioRoute = option)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playDtmf(digit: Char) {
        try {
            currentCall?.playDtmfTone(digit)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopDtmf() {
        try {
            currentCall?.stopDtmfTone()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchInCallScreen(context: Context) {
        try {
            val intent = Intent(context, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractPhoneNumber(call: Call): String {
        return try {
            call.details?.handle?.schemeSpecificPart ?: "رقم غير معروف"
        } catch (e: Exception) {
            "رقم غير معروف"
        }
    }

    @Suppress("DEPRECATION")
    private fun getCallStateCompat(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: call.state
        } else {
            call.state
        }
    }

    private fun mapTelecomState(state: Int): CallUiState {
        return when (state) {
            Call.STATE_DIALING -> CallUiState.DIALING
            Call.STATE_RINGING -> CallUiState.RINGING
            Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_NEW -> CallUiState.CONNECTING
            Call.STATE_ACTIVE -> CallUiState.ACTIVE
            Call.STATE_HOLDING -> CallUiState.HOLDING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallUiState.DISCONNECTED
            else -> CallUiState.CONNECTING
        }
    }
}

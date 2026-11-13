package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.ui.call.InCallActivity

object CallNotificationManager {

    private const val INCOMING_CHANNEL_ID = "contacts_pro_incoming_calls_v1"
    private const val ONGOING_CHANNEL_ID = "contacts_pro_ongoing_calls_v1"
    private const val CALL_NOTIFICATION_ID = 77821

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (nm.getNotificationChannel(INCOMING_CHANNEL_ID) == null) {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val incomingChannel = NotificationChannel(
                    INCOMING_CHANNEL_ID,
                    "المكالمات الواردة",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات رنين المكالمات الهاتفية الواردة"
                    setSound(
                        ringtoneUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(incomingChannel)
            }

            if (nm.getNotificationChannel(ONGOING_CHANNEL_ID) == null) {
                val ongoingChannel = NotificationChannel(
                    ONGOING_CHANNEL_ID,
                    "المكالمات الجارية",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إشعار المكالمة النشطة حالياً"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ongoingChannel)
            }
        }
    }

    fun showIncomingCallNotification(context: Context, callerName: String, phoneNumber: String) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = callerName.ifBlank { phoneNumber.ifBlank { "مكالمة واردة" } }
        val callerPerson = Person.Builder()
            .setName(displayTitle)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(displayTitle)
            .setContentText("مكالمة واردة • $phoneNumber")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .build()

        nm.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun showOngoingCallNotification(context: Context, callerName: String, phoneNumber: String, statusText: String) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openCallIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openCallPendingIntent = PendingIntent.getActivity(
            context,
            104,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getBroadcast(
            context,
            105,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = callerName.ifBlank { phoneNumber.ifBlank { "مكالمة جارية" } }
        val callerPerson = Person.Builder()
            .setName(displayTitle)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(displayTitle)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(openCallPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    callerPerson,
                    hangupPendingIntent
                )
            )
            .build()

        nm.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(CALL_NOTIFICATION_ID)
    }
}

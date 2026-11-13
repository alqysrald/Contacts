package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ANSWER -> CallManager.answerCall()
            ACTION_DECLINE -> CallManager.rejectCall()
            ACTION_HANGUP -> CallManager.disconnectCall()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.contacts.app.pro.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE = "com.contacts.app.pro.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP = "com.contacts.app.pro.ACTION_HANGUP_CALL"
    }
}

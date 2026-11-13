package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallDirection(val arabicLabel: String) {
    INCOMING("واردة"),
    OUTGOING("صادرة"),
    MISSED("فائتة"),
    REJECTED("مرفوضة"),
    BLOCKED("محظورة")
}

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val cachedName: String? = null,
    val direction: String = CallDirection.OUTGOING.name,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val isRead: Boolean = true
)

data class CallLogItem(
    val id: String,
    val localId: Long? = null,
    val systemLogId: Long? = null,
    val number: String,
    val displayName: String,
    val isUnknownNumber: Boolean,
    val direction: CallDirection,
    val timestamp: Long,
    val durationSeconds: Long,
    val avatarColorHex: String = "#D4AF37",
    val contactId: Long? = null
) {
    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercase() ?: "#"
}

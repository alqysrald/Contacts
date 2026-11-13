package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.local.CallLogDao
import com.example.data.local.ContactDao
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntity
import com.example.data.model.CallLogItem
import com.example.data.model.ContactEntity
import com.example.data.vcard.VCardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CallLogRepository(
    private val context: Context,
    private val callLogDao: CallLogDao,
    private val contactDao: ContactDao
) {
    val localCallLogsFlow: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogsFlow()

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun recordLocalCall(
        number: String,
        cachedName: String?,
        direction: CallDirection,
        timestamp: Long,
        durationSeconds: Long
    ) = withContext(Dispatchers.IO) {
        val cleanNum = number.trim()
        if (cleanNum.isEmpty()) return@withContext
        val resolvedName = cachedName?.takeIf { it.isNotBlank() } ?: resolveContactName(cleanNum)
        callLogDao.insert(
            CallLogEntity(
                number = cleanNum,
                cachedName = resolvedName,
                direction = direction.name,
                timestamp = timestamp,
                durationSeconds = durationSeconds,
                isRead = direction != CallDirection.MISSED
            )
        )
    }

    suspend fun loadCombinedCallLogs(): List<CallLogItem> = withContext(Dispatchers.IO) {
        val allContacts = contactDao.getAllContactsSnapshot()
        val contactLookup = buildContactPhoneIndex(allContacts)

        val systemLogs = if (hasCallLogPermission()) {
            querySystemCallLogs(contactLookup)
        } else {
            emptyList()
        }

        val localLogs = callLogDao.getAllCallLogsSnapshot().map { entity ->
            val cleanKey = normalizePhoneKey(entity.number)
            val matchedContact = contactLookup[cleanKey]
            val dir = try {
                CallDirection.valueOf(entity.direction)
            } catch (e: Exception) {
                CallDirection.OUTGOING
            }
            val display = matchedContact?.displayName
                ?: entity.cachedName?.takeIf { it.isNotBlank() }
                ?: entity.number

            CallLogItem(
                id = "local_${entity.id}",
                localId = entity.id,
                systemLogId = null,
                number = entity.number,
                displayName = display,
                isUnknownNumber = matchedContact == null && entity.cachedName.isNullOrBlank(),
                direction = dir,
                timestamp = entity.timestamp,
                durationSeconds = entity.durationSeconds,
                avatarColorHex = matchedContact?.avatarColorHex ?: VCardManager.getRandomPaletteColor(display),
                contactId = matchedContact?.id
            )
        }

        // Merge and deduplicate entries that share the same number & approximate timestamp (within 10 seconds)
        val merged = mutableListOf<CallLogItem>()
        merged.addAll(systemLogs)

        for (local in localLogs) {
            val localKey = normalizePhoneKey(local.number)
            val isDuplicateInSystem = systemLogs.any { sys ->
                normalizePhoneKey(sys.number) == localKey &&
                        kotlin.math.abs(sys.timestamp - local.timestamp) < 15_000L
            }
            if (!isDuplicateInSystem) {
                merged.add(local)
            }
        }

        merged.sortedByDescending { it.timestamp }
    }

    private fun querySystemCallLogs(contactLookup: Map<String, ContactEntity>): List<CallLogItem> {
        val list = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val sysId = cursor.getLong(idIdx)
                    val number = cursor.getString(numIdx)?.takeIf { it.isNotBlank() } ?: "رقم خاص"
                    val cachedName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                    val typeInt = cursor.getInt(typeIdx)
                    val dateMillis = cursor.getLong(dateIdx)
                    val duration = cursor.getLong(durIdx)

                    val direction = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallDirection.BLOCKED
                        else -> CallDirection.INCOMING
                    }

                    val matchedContact = contactLookup[normalizePhoneKey(number)]
                    val display = matchedContact?.displayName ?: cachedName ?: number

                    list.add(
                        CallLogItem(
                            id = "sys_$sysId",
                            localId = null,
                            systemLogId = sysId,
                            number = number,
                            displayName = display,
                            isUnknownNumber = matchedContact == null && cachedName == null,
                            direction = direction,
                            timestamp = dateMillis,
                            durationSeconds = duration,
                            avatarColorHex = matchedContact?.avatarColorHex ?: VCardManager.getRandomPaletteColor(display),
                            contactId = matchedContact?.id
                        )
                    )
                    count++
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    suspend fun deleteCallLogItem(item: CallLogItem) = withContext(Dispatchers.IO) {
        item.localId?.let { callLogDao.deleteById(it) }
        item.systemLogId?.let { sysId ->
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    context.contentResolver.delete(
                        CallLog.Calls.CONTENT_URI,
                        "${CallLog.Calls._ID} = ?",
                        arrayOf(sysId.toString())
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun clearAllCallLogs() = withContext(Dispatchers.IO) {
        callLogDao.clearAll()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun resolveContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        val cleanKey = normalizePhoneKey(phoneNumber)
        if (cleanKey.isNotEmpty()) {
            val contacts = contactDao.getAllContactsSnapshot()
            val match = buildContactPhoneIndex(contacts)[cleanKey]
            if (match != null) return@withContext match.displayName
        }

        // Fallback to Android system PhoneLookup
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return@withContext cursor.getString(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        null
    }

    private fun buildContactPhoneIndex(contacts: List<ContactEntity>): Map<String, ContactEntity> {
        val map = HashMap<String, ContactEntity>(contacts.size * 2)
        for (entity in contacts) {
            if (entity.isVaultProtected) continue // Keep vault contacts hidden from call log caller ID
            val phones = parsePhonesJson(entity.phonesJson)
            for (p in phones) {
                val key = normalizePhoneKey(p.number)
                if (key.isNotEmpty()) {
                    map[key] = entity
                }
            }
        }
        return map
    }

    companion object {
        fun normalizePhoneKey(raw: String): String {
            val digits = raw.replace("[^0-9]".toRegex(), "")
            return if (digits.length > 9) digits.takeLast(9) else digits
        }
    }
}

package com.example.data.repository

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.example.data.local.ContactDao
import com.example.data.model.ContactEntity
import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.data.security.CryptoManager
import com.example.data.vcard.VCardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ContactsRepository(
    private val context: Context,
    private val contactDao: ContactDao
) {
    private val contentResolver: ContentResolver = context.contentResolver

    val normalContactsFlow: Flow<List<ContactItem>> = contactDao.getAllNormalContacts().map { entities ->
        entities.map { it.toContactItem() }
    }

    val vaultContactsFlow: Flow<List<ContactItem>> = contactDao.getVaultContacts().map { entities ->
        entities.map { it.toContactItem() }
    }

    val favoriteContactsFlow: Flow<List<ContactItem>> = contactDao.getFavoriteContacts().map { entities ->
        entities.map { it.toContactItem() }
    }

    suspend fun getContactById(id: Long): ContactItem? = withContext(Dispatchers.IO) {
        contactDao.getContactById(id)?.toContactItem()
    }

    suspend fun saveContact(item: ContactItem): Long = withContext(Dispatchers.IO) {
        val entity = item.toContactEntity()
        val savedId = contactDao.insert(entity)

        // If not a secret vault contact and system permission is available, also save or update in system contacts
        if (!item.isVault) {
            try {
                syncContactToSystem(item.copy(id = savedId))
            } catch (e: Exception) {
                // Graceful fallback if write permission not granted
                e.printStackTrace()
            }
        }
        savedId
    }

    suspend fun updateContact(item: ContactItem) = withContext(Dispatchers.IO) {
        val entity = item.toContactEntity()
        contactDao.update(entity)
    }

    suspend fun deleteContact(id: Long) = withContext(Dispatchers.IO) {
        val item = contactDao.getContactById(id)
        if (item?.systemContactId != null) {
            try {
                val deleteUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, item.systemContactId.toString())
                contentResolver.delete(deleteUri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        contactDao.deleteById(id)
    }

    suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(id) ?: return@withContext
        val updated = contact.copy(isFavorite = !contact.isFavorite, updatedAt = System.currentTimeMillis())
        contactDao.update(updated)
    }

    suspend fun moveToVault(id: Long, moveToVault: Boolean) = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(id) ?: return@withContext
        val updated = contact.copy(
            isVaultProtected = moveToVault,
            category = if (moveToVault) "الخزنة المشفرة" else "عام",
            updatedAt = System.currentTimeMillis()
        )
        contactDao.update(updated)
    }

    /**
     * Reads all device system contacts via ContentResolver and merges into Room database
     */
    suspend fun syncDeviceContacts(): Int = withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<Long, MutableContactBuilder>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val starredIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "جهة اتصال بدون اسم"
                    val number = cursor.getString(numberIdx) ?: ""
                    val typeInt = cursor.getInt(typeIdx)
                    val isStarred = cursor.getInt(starredIdx) == 1

                    val typeStr = when (typeInt) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "المنزل"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "العمل"
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "جوال"
                        else -> "هاتف"
                    }

                    val builder = contactsMap.getOrPut(contactId) {
                        MutableContactBuilder(
                            systemId = contactId,
                            displayName = name,
                            isStarred = isStarred
                        )
                    }
                    if (number.isNotBlank()) {
                        builder.phones.add(PhoneEntry(typeStr, number.replace("\\s".toRegex(), "")))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
            return@withContext 0
        }

        // Convert to entities
        val entities = contactsMap.values.map { b ->
            val primary = b.phones.firstOrNull()?.number ?: ""
            ContactEntity(
                systemContactId = b.systemId,
                displayName = b.displayName,
                phonesJson = phonesToJson(b.phones),
                emailsJson = "[]",
                category = "عام",
                isFavorite = b.isStarred,
                isVaultProtected = false,
                avatarColorHex = VCardManager.getRandomPaletteColor(b.displayName)
            )
        }

        if (entities.isNotEmpty()) {
            contactDao.clearNormalContacts()
            // Batch insert in chunks of 250 for speed and low memory
            entities.chunked(250).forEach { chunk ->
                contactDao.insertAll(chunk)
            }
        }

        entities.size
    }

    /**
     * Imports up to 2000+ contacts in batches from a VCF file
     */
    suspend fun importVCardStream(inputStream: InputStream, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val parsedList = VCardManager.parseVCard(inputStream)
        val total = parsedList.size
        var imported = 0

        parsedList.chunked(200).forEach { chunk ->
            val entities = chunk.map { it.toContactEntity(autoId = true) }
            contactDao.insertAll(entities)
            imported += chunk.size
            onProgress(imported, total)
        }

        imported
    }

    /**
     * Export all contacts to VCF
     */
    suspend fun exportAllContactsToVcf(includeVault: Boolean = false): String = withContext(Dispatchers.IO) {
        val entities = contactDao.getAllContactsSnapshot()
        val filtered = if (includeVault) entities else entities.filter { !it.isVaultProtected }
        val items = filtered.map { it.toContactItem() }
        VCardManager.exportToVCard(items)
    }

    /**
     * Export contacts to Encrypted Backup
     */
    suspend fun exportEncryptedBackup(password: String): String = withContext(Dispatchers.IO) {
        val entities = contactDao.getAllContactsSnapshot()
        val items = entities.map { it.toContactItem() }
        VCardManager.createEncryptedBackup(items, password)
    }

    /**
     * Restore from Encrypted Backup
     */
    suspend fun restoreEncryptedBackup(backupContent: String, password: String): Result<Int> = withContext(Dispatchers.IO) {
        val result = VCardManager.restoreEncryptedBackup(backupContent, password)
        if (result.isSuccess) {
            val contacts = result.getOrThrow()
            contacts.chunked(200).forEach { chunk ->
                val entities = chunk.map { it.toContactEntity(autoId = true) }
                contactDao.insertAll(entities)
            }
            Result.success(contacts.size)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("فشل الاستعادة"))
        }
    }

    private fun syncContactToSystem(item: ContactItem) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, item.displayName)
                .build()
        )

        for (p in item.phoneNumbers) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, p.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
        }

        if (item.emails.isNotEmpty()) {
            for (email in item.emails) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                        .build()
                )
            }
        }

        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private data class MutableContactBuilder(
        val systemId: Long,
        val displayName: String,
        val isStarred: Boolean,
        val phones: MutableList<PhoneEntry> = mutableListOf()
    )
}

// Extension converters between Entity and Item
fun ContactEntity.toContactItem(): ContactItem {
    val phones = parsePhonesJson(this.phonesJson)
    val emails = parseEmailsJson(this.emailsJson)
    val decryptedNotes = if (this.encryptedNotes.isNotEmpty()) {
        CryptoManager.decrypt(this.encryptedNotes)
    } else ""

    return ContactItem(
        id = this.id,
        systemContactId = this.systemContactId,
        displayName = this.displayName,
        primaryPhone = phones.firstOrNull()?.number ?: "",
        phoneNumbers = phones,
        emails = emails,
        organization = this.organization,
        jobTitle = this.jobTitle,
        category = this.category,
        isFavorite = this.isFavorite,
        isVault = this.isVaultProtected,
        birthday = this.birthdayDate,
        anniversary = this.anniversaryDate,
        notes = decryptedNotes,
        address = this.address,
        avatarColorHex = this.avatarColorHex,
        isSystemContact = this.systemContactId != null
    )
}

fun ContactItem.toContactEntity(autoId: Boolean = false): ContactEntity {
    val encryptedNotes = if (this.notes.isNotEmpty()) {
        CryptoManager.encrypt(this.notes)
    } else ""

    return ContactEntity(
        id = if (autoId) 0 else this.id,
        systemContactId = this.systemContactId,
        displayName = this.displayName,
        phonesJson = phonesToJson(this.phoneNumbers),
        emailsJson = emailsToJson(this.emails),
        organization = this.organization,
        jobTitle = this.jobTitle,
        category = this.category,
        isFavorite = this.isFavorite,
        isVaultProtected = this.isVault,
        birthdayDate = this.birthday,
        anniversaryDate = this.anniversary,
        encryptedNotes = encryptedNotes,
        address = this.address,
        avatarColorHex = this.avatarColorHex,
        updatedAt = System.currentTimeMillis()
    )
}

fun phonesToJson(phones: List<PhoneEntry>): String {
    val arr = JSONArray()
    for (p in phones) {
        val o = JSONObject()
        o.put("type", p.type)
        o.put("number", p.number)
        arr.put(o)
    }
    return arr.toString()
}

fun parsePhonesJson(json: String?): List<PhoneEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<PhoneEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(PhoneEntry(o.optString("type", "جوال"), o.getString("number")))
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun emailsToJson(emails: List<String>): String {
    val arr = JSONArray()
    for (e in emails) arr.put(e)
    return arr.toString()
}

fun parseEmailsJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        list
    } catch (e: Exception) {
        emptyList()
    }
}

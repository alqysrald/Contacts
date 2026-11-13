package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val systemContactId: Long? = null,
    val displayName: String,
    val phonesJson: String = "[]", // [{"type":"جوال","number":"778215553"}]
    val emailsJson: String = "[]",
    val organization: String = "",
    val jobTitle: String = "",
    val category: String = "عام", // العائلة, العمل, الأصدقاء, مهم جداً, الخزنة المشفرة
    val isFavorite: Boolean = false,
    val isVaultProtected: Boolean = false, // Private encrypted vault
    val birthdayDate: String? = null, // YYYY-MM-DD or MM-DD
    val anniversaryDate: String? = null,
    val encryptedNotes: String = "",
    val address: String = "",
    val avatarColorHex: String = "#D4AF37",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PhoneEntry(
    val type: String = "جوال",
    val number: String
)

data class ContactItem(
    val id: Long,
    val systemContactId: Long? = null,
    val displayName: String,
    val primaryPhone: String,
    val phoneNumbers: List<PhoneEntry> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String = "",
    val jobTitle: String = "",
    val category: String = "عام",
    val isFavorite: Boolean = false,
    val isVault: Boolean = false,
    val birthday: String? = null,
    val anniversary: String? = null,
    val notes: String = "",
    val address: String = "",
    val avatarColorHex: String = "#D4AF37",
    val isSystemContact: Boolean = false
) {
    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercase() ?: "؟"
}

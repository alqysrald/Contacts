package com.example.data.vcard

import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.data.security.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VCardManager {

    /**
     * Exports a list of contacts into standard vCard 3.0 format (.vcf).
     */
    fun exportToVCard(contacts: List<ContactItem>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.append("BEGIN:VCARD\r\n")
            sb.append("VERSION:3.0\r\n")
            sb.append("FN:").append(c.displayName.trim().replace("\n", " ")).append("\r\n")
            sb.append("N:;").append(c.displayName.trim().replace("\n", " ")).append(";;;\r\n")

            if (c.phoneNumbers.isNotEmpty()) {
                for (p in c.phoneNumbers) {
                    val cleanPhone = p.number.trim()
                    if (cleanPhone.isNotEmpty()) {
                        sb.append("TEL;TYPE=CELL:").append(cleanPhone).append("\r\n")
                    }
                }
            } else if (c.primaryPhone.isNotBlank()) {
                sb.append("TEL;TYPE=CELL:").append(c.primaryPhone.trim()).append("\r\n")
            }

            for (email in c.emails) {
                if (email.isNotBlank()) {
                    sb.append("EMAIL;TYPE=INTERNET:").append(email.trim()).append("\r\n")
                }
            }

            if (c.organization.isNotBlank()) {
                sb.append("ORG:").append(c.organization.trim()).append("\r\n")
            }
            if (c.jobTitle.isNotBlank()) {
                sb.append("TITLE:").append(c.jobTitle.trim()).append("\r\n")
            }
            if (!c.birthday.isNullOrBlank()) {
                sb.append("BDAY:").append(c.birthday.trim()).append("\r\n")
            }
            if (c.address.isNotBlank()) {
                sb.append("ADR:;;").append(c.address.trim()).append(";;;;\r\n")
            }
            if (c.notes.isNotBlank()) {
                sb.append("NOTE:").append(c.notes.trim().replace("\n", "\\n")).append("\r\n")
            }
            if (c.category.isNotBlank() && c.category != "عام") {
                sb.append("CATEGORIES:").append(c.category.trim()).append("\r\n")
            }

            sb.append("END:VCARD\r\n")
        }
        return sb.toString()
    }

    /**
     * Parses a VCF InputStream supporting up to 2000+ contacts efficiently.
     */
    fun parseVCard(inputStream: InputStream): List<ContactItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val contacts = mutableListOf<ContactItem>()

        var currentName = ""
        val currentPhones = mutableListOf<PhoneEntry>()
        val currentEmails = mutableListOf<String>()
        var currentOrg = ""
        var currentTitle = ""
        var currentBday = ""
        var currentNote = ""
        var currentAddr = ""
        var currentCat = "عام"
        var insideVCard = false

        var line = reader.readLine()
        var idCounter = 1L

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
                insideVCard = true
                currentName = ""
                currentPhones.clear()
                currentEmails.clear()
                currentOrg = ""
                currentTitle = ""
                currentBday = ""
                currentNote = ""
                currentAddr = ""
                currentCat = "عام"
            } else if (trimmed.equals("END:VCARD", ignoreCase = true)) {
                if (insideVCard && (currentName.isNotEmpty() || currentPhones.isNotEmpty())) {
                    val finalName = if (currentName.isBlank()) {
                        currentPhones.firstOrNull()?.number ?: "جهة اتصال بدون اسم"
                    } else currentName

                    val primary = currentPhones.firstOrNull()?.number ?: ""
                    contacts.add(
                        ContactItem(
                            id = idCounter++,
                            displayName = finalName,
                            primaryPhone = primary,
                            phoneNumbers = currentPhones.toList(),
                            emails = currentEmails.toList(),
                            organization = currentOrg,
                            jobTitle = currentTitle,
                            birthday = if (currentBday.isNotBlank()) currentBday else null,
                            notes = currentNote,
                            address = currentAddr,
                            category = currentCat,
                            avatarColorHex = getRandomPaletteColor(finalName)
                        )
                    )
                }
                insideVCard = false
            } else if (insideVCard) {
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx > 0) {
                    val rawKey = trimmed.substring(0, colonIdx).uppercase(Locale.ROOT)
                    val rawValue = trimmed.substring(colonIdx + 1).trim()

                    when {
                        rawKey == "FN" || (rawKey.startsWith("FN;") && currentName.isEmpty()) -> {
                            currentName = rawValue.replace(";", " ").trim()
                        }
                        rawKey.startsWith("N") && !rawKey.startsWith("NOTE") && currentName.isEmpty() -> {
                            // Format: Surname;GivenName;AdditionalNames;Prefix;Suffix
                            val parts = rawValue.split(";").filter { it.isNotBlank() }
                            if (parts.isNotEmpty()) {
                                currentName = parts.reversed().joinToString(" ").trim()
                            }
                        }
                        rawKey.startsWith("TEL") -> {
                            val cleanNumber = rawValue.replace("[^0-9+*#]".toRegex(), "")
                            if (cleanNumber.isNotEmpty()) {
                                val type = if (rawKey.contains("WORK")) "العمل"
                                else if (rawKey.contains("HOME")) "المنزل"
                                else "جوال"
                                currentPhones.add(PhoneEntry(type, cleanNumber))
                            }
                        }
                        rawKey.startsWith("EMAIL") -> {
                            if (rawValue.contains("@")) {
                                currentEmails.add(rawValue)
                            }
                        }
                        rawKey.startsWith("ORG") -> {
                            currentOrg = rawValue.replace(";", " - ").trim()
                        }
                        rawKey.startsWith("TITLE") -> {
                            currentTitle = rawValue.trim()
                        }
                        rawKey.startsWith("BDAY") -> {
                            currentBday = rawValue.take(10).trim()
                        }
                        rawKey.startsWith("NOTE") -> {
                            currentNote = rawValue.replace("\\n", "\n").trim()
                        }
                        rawKey.startsWith("ADR") -> {
                            currentAddr = rawValue.replace(";", " ").trim()
                        }
                        rawKey.startsWith("CATEGORIES") -> {
                            val cat = rawValue.split(",").firstOrNull()?.trim() ?: "عام"
                            if (cat.isNotBlank()) currentCat = cat
                        }
                    }
                }
            }
            line = reader.readLine()
        }

        return contacts
    }

    /**
     * Exports full contacts data into an encrypted JSON backup file.
     */
    fun createEncryptedBackup(contacts: List<ContactItem>, password: String): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("appName", "الأسماء Contacts Pro")
        root.put("timestamp", System.currentTimeMillis())
        root.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        root.put("totalCount", contacts.size)
        root.put("verifyToken", CryptoManager.createVerificationProbe(password))

        val array = JSONArray()
        for (c in contacts) {
            val obj = JSONObject()
            obj.put("displayName", c.displayName)
            obj.put("primaryPhone", c.primaryPhone)

            val phonesArr = JSONArray()
            for (p in c.phoneNumbers) {
                val po = JSONObject()
                po.put("type", p.type)
                po.put("number", p.number)
                phonesArr.put(po)
            }
            obj.put("phoneNumbers", phonesArr)

            val emailsArr = JSONArray()
            for (e in c.emails) emailsArr.put(e)
            obj.put("emails", emailsArr)

            obj.put("organization", c.organization)
            obj.put("jobTitle", c.jobTitle)
            obj.put("category", c.category)
            obj.put("isFavorite", c.isFavorite)
            obj.put("isVault", c.isVault)
            obj.put("birthday", c.birthday ?: "")
            obj.put("anniversary", c.anniversary ?: "")
            obj.put("notes", c.notes)
            obj.put("address", c.address)
            obj.put("avatarColorHex", c.avatarColorHex)

            array.put(obj)
        }

        val rawContactsString = array.toString()
        val encryptedPayload = CryptoManager.encrypt(rawContactsString, password)
        root.put("encryptedPayload", encryptedPayload)

        return root.toString(2)
    }

    /**
     * Restores contacts from an encrypted backup string.
     */
    fun restoreEncryptedBackup(backupJsonString: String, password: String): Result<List<ContactItem>> {
        return try {
            val root = JSONObject(backupJsonString)
            val verifyToken = root.optString("verifyToken", "")
            if (verifyToken.isNotEmpty()) {
                val isValid = CryptoManager.verifyPassword(verifyToken, password)
                if (!isValid) {
                    return Result.failure(Exception("كلمة المرور غير صحيحة لتفكيك التشفير"))
                }
            }

            val encryptedPayload = root.getString("encryptedPayload")
            val decryptedJson = CryptoManager.decrypt(encryptedPayload, password)
            if (decryptedJson.startsWith("[محتوى مشفر") || decryptedJson.isBlank()) {
                return Result.failure(Exception("فشل فك التشفير. تأكد من كلمة المرور."))
            }

            val array = JSONArray(decryptedJson)
            val list = mutableListOf<ContactItem>()
            var idCounter = 1L

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val phones = mutableListOf<PhoneEntry>()
                val phonesArr = obj.optJSONArray("phoneNumbers")
                if (phonesArr != null) {
                    for (p in 0 until phonesArr.length()) {
                        val po = phonesArr.getJSONObject(p)
                        phones.add(PhoneEntry(po.getString("type"), po.getString("number")))
                    }
                }

                val emails = mutableListOf<String>()
                val emailsArr = obj.optJSONArray("emails")
                if (emailsArr != null) {
                    for (e in 0 until emailsArr.length()) {
                        emails.add(emailsArr.getString(e))
                    }
                }

                val primary = obj.optString("primaryPhone", phones.firstOrNull()?.number ?: "")
                list.add(
                    ContactItem(
                        id = idCounter++,
                        displayName = obj.optString("displayName", "جهة اتصال"),
                        primaryPhone = primary,
                        phoneNumbers = phones,
                        emails = emails,
                        organization = obj.optString("organization", ""),
                        jobTitle = obj.optString("jobTitle", ""),
                        category = obj.optString("category", "عام"),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        isVault = obj.optBoolean("isVault", false),
                        birthday = obj.optString("birthday").takeIf { it.isNotBlank() },
                        anniversary = obj.optString("anniversary").takeIf { it.isNotBlank() },
                        notes = obj.optString("notes", ""),
                        address = obj.optString("address", ""),
                        avatarColorHex = obj.optString("avatarColorHex", getRandomPaletteColor(obj.optString("displayName", "")))
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val PALETTE = listOf(
        "#D4AF37", "#E6AF2E", "#10B981", "#3B82F6",
        "#8B5CF6", "#EC4899", "#F59E0B", "#14B8A6",
        "#6366F1", "#06B6D4", "#F43F5E", "#84CC16"
    )

    fun getRandomPaletteColor(name: String): String {
        if (name.isEmpty()) return PALETTE[0]
        val index = (Math.abs(name.hashCode()) % PALETTE.size)
        return PALETTE[index]
    }
}

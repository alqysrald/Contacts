package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.data.repository.CallLogRepository
import com.example.data.security.CryptoManager
import com.example.data.vcard.VCardManager
import com.example.telecom.DefaultDialerHelper
import com.example.ui.call.formatCallDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("الأسماء", appName)
    }

    @Test
    fun testCryptoManagerEncryptionDecryption() {
        val originalText = "ملاحظات سرية محمية بتشفير عالي جداً"
        val password = "StrongMasterPassword123"
        val encrypted = CryptoManager.encrypt(originalText, password)
        assertTrue(encrypted.isNotBlank())

        val decrypted = CryptoManager.decrypt(encrypted, password)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testVCardExportAndParse() {
        val contacts = listOf(
            ContactItem(
                id = 1L,
                displayName = "عبدالحميد داوؤد",
                primaryPhone = "778215553",
                phoneNumbers = listOf(PhoneEntry("جوال", "778215553")),
                organization = "فريق التطوير",
                jobTitle = "المبرمج والمطور"
            )
        )

        val vcfString = VCardManager.exportToVCard(contacts)
        assertTrue(vcfString.contains("FN:عبدالحميد داوؤد"))
        assertTrue(vcfString.contains("TEL;TYPE=CELL:778215553"))

        val parsed = VCardManager.parseVCard(ByteArrayInputStream(vcfString.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsed.size)
        assertEquals("عبدالحميد داوؤد", parsed[0].displayName)
        assertEquals("778215553", parsed[0].primaryPhone)
    }

    @Test
    fun testCallLogNormalizationAndDurationFormatting() {
        assertEquals("778215553", CallLogRepository.normalizePhoneKey("+967 778-215-553"))
        assertEquals("778215553", CallLogRepository.normalizePhoneKey("00967778215553"))
        assertEquals("01:05", formatCallDuration(65))
        assertEquals("01:01:05", formatCallDuration(3665))
    }

    @Test
    fun testDefaultDialerIntentCreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent: Intent = DefaultDialerHelper.createDefaultDialerIntent(context)
        assertNotNull(intent)
    }
}

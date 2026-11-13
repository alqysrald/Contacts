package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.CallDirection
import com.example.data.model.CallLogItem
import com.example.data.model.ContactItem
import com.example.data.model.PhoneEntry
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsRepository
import com.example.telecom.DefaultDialerHelper
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactsRepository
    private val callLogRepository: CallLogRepository
    val context: Context = application.applicationContext

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ContactsRepository(application, db.contactDao())
        callLogRepository = CallLogRepository(application, db.callLogDao(), db.contactDao())
        viewModelScope.launch {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                repository.syncDeviceContacts()
            }
            refreshCallLogs()
        }
        viewModelScope.launch {
            callLogRepository.localCallLogsFlow.collect {
                refreshCallLogs()
            }
        }
    }

    // UI state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("الكل")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.ROYAL_GOLD)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _vaultPin = MutableStateFlow("1234") // Default master PIN
    val vaultPin: StateFlow<String> = _vaultPin.asStateFlow()

    private val _dialerNumber = MutableStateFlow("")
    val dialerNumber: StateFlow<String> = _dialerNumber.asStateFlow()

    private val _showDialpadOverlay = MutableStateFlow(false)
    val showDialpadOverlay: StateFlow<Boolean> = _showDialpadOverlay.asStateFlow()

    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _selectedContact = MutableStateFlow<ContactItem?>(null)
    val selectedContact: StateFlow<ContactItem?> = _selectedContact.asStateFlow()

    private val _isEditingContact = MutableStateFlow(false)
    val isEditingContact: StateFlow<Boolean> = _isEditingContact.asStateFlow()

    private val _lastCloudBackupTime = MutableStateFlow<String?>(null)
    val lastCloudBackupTime: StateFlow<String?> = _lastCloudBackupTime.asStateFlow()

    private val _allCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    private val _callLogFilter = MutableStateFlow("الكل")
    val callLogFilter: StateFlow<String> = _callLogFilter.asStateFlow()

    private val _callLogSearchQuery = MutableStateFlow("")
    val callLogSearchQuery: StateFlow<String> = _callLogSearchQuery.asStateFlow()

    private val _isDefaultDialer = MutableStateFlow(DefaultDialerHelper.isDefaultDialer(application))
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    // Combined filtered contacts flow
    val displayedContacts: StateFlow<List<ContactItem>> = combine(
        repository.normalContactsFlow,
        repository.vaultContactsFlow,
        _searchQuery,
        _selectedCategory,
        _isVaultUnlocked
    ) { normalList, vaultList, query, category, vaultUnlocked ->
        val fullList = if (vaultUnlocked && category == "الخزنة المشفرة") {
            vaultList
        } else if (category == "الخزنة المشفرة") {
            emptyList()
        } else {
            normalList
        }

        fullList.filter { contact ->
            val matchesCategory = when (category) {
                "الكل" -> true
                "المفضلة" -> contact.isFavorite
                else -> contact.category == category
            }

            val matchesQuery = if (query.isBlank()) true else {
                val cleanQ = query.trim().lowercase(Locale.ROOT)
                val cleanDigits = query.replace("[^0-9+]".toRegex(), "")
                val nameMatch = contact.displayName.lowercase(Locale.ROOT).contains(cleanQ)
                val orgMatch = contact.organization.lowercase(Locale.ROOT).contains(cleanQ)
                val notesMatch = contact.notes.lowercase(Locale.ROOT).contains(cleanQ)
                val phoneMatch = if (cleanDigits.isNotEmpty()) {
                    contact.phoneNumbers.any { it.number.contains(cleanDigits) } ||
                            contact.primaryPhone.contains(cleanDigits)
                } else false

                nameMatch || orgMatch || notesMatch || phoneMatch
            }

            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteContacts: StateFlow<List<ContactItem>> = repository.favoriteContactsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val filteredCallLogs: StateFlow<List<CallLogItem>> = combine(
        _allCallLogs,
        _callLogFilter,
        _callLogSearchQuery
    ) { logs, filter, query ->
        logs.filter { item ->
            val matchesFilter = when (filter) {
                "فائتة" -> item.direction == CallDirection.MISSED || item.direction == CallDirection.REJECTED
                "صادرة" -> item.direction == CallDirection.OUTGOING
                "واردة" -> item.direction == CallDirection.INCOMING
                else -> true
            }
            val matchesQuery = if (query.isBlank()) true else {
                val cleanQ = query.trim().lowercase(Locale.ROOT)
                item.displayName.lowercase(Locale.ROOT).contains(cleanQ) ||
                    item.number.contains(cleanQ)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogStats: StateFlow<Map<String, Int>> = combine(_allCallLogs, _callLogFilter) { logs, _ ->
        mapOf(
            "all" to logs.size,
            "missed" to logs.count { it.direction == CallDirection.MISSED || it.direction == CallDirection.REJECTED },
            "outgoing" to logs.count { it.direction == CallDirection.OUTGOING },
            "incoming" to logs.count { it.direction == CallDirection.INCOMING }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun setCallLogFilter(filter: String) {
        _callLogFilter.value = filter
    }

    fun setCallLogSearchQuery(query: String) {
        _callLogSearchQuery.value = query
    }

    fun refreshDefaultDialerStatus() {
        _isDefaultDialer.value = DefaultDialerHelper.isDefaultDialer(context)
    }

    fun refreshCallLogs() {
        viewModelScope.launch {
            _allCallLogs.value = callLogRepository.loadCombinedCallLogs()
        }
    }

    fun deleteCallLogItem(item: CallLogItem) {
        viewModelScope.launch {
            callLogRepository.deleteCallLogItem(item)
            refreshCallLogs()
            showToast("تم حذف السجل بنجاح")
        }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch {
            callLogRepository.clearAllCallLogs()
            refreshCallLogs()
            showToast("تم مسح سجل المكالمات بالكامل")
        }
    }

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
        _showDialpadOverlay.value = false
        if (tab == 1) {
            refreshCallLogs()
        }
    }

    fun openDialpad(initialNumber: String = "") {
        if (initialNumber.isNotEmpty()) {
            _dialerNumber.value = initialNumber
        }
        _showDialpadOverlay.value = true
    }

    fun closeDialpad() {
        _showDialpadOverlay.value = false
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun selectContact(contact: ContactItem?) {
        _selectedContact.value = contact
        _isEditingContact.value = false
    }

    fun startEditContact(contact: ContactItem?) {
        _selectedContact.value = contact
        _isEditingContact.value = true
    }

    fun cancelEdit() {
        _isEditingContact.value = false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showToast(msg: String) {
        _statusMessage.value = msg
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
            // Update selected contact if active
            _selectedContact.value?.let { current ->
                if (current.id == id) {
                    _selectedContact.value = current.copy(isFavorite = !current.isFavorite)
                }
            }
        }
    }

    fun saveContact(item: ContactItem) {
        viewModelScope.launch {
            if (item.displayName.isBlank() && item.primaryPhone.isBlank() && item.phoneNumbers.isEmpty()) {
                showToast("يرجى كتابة الاسم أو رقم الهاتف على الأقل")
                return@launch
            }
            repository.saveContact(item)
            _isEditingContact.value = false
            _selectedContact.value = null
            _showDialpadOverlay.value = false
            showToast("تم حفظ جهة الاتصال بنجاح ✓")
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
            _selectedContact.value = null
            _isEditingContact.value = false
            showToast("تم حذف جهة الاتصال بنجاح")
        }
    }

    fun moveContactToVault(id: Long, toVault: Boolean) {
        viewModelScope.launch {
            repository.moveToVault(id, toVault)
            _selectedContact.value = null
            showToast(if (toVault) "تم نقل جهة الاتصال إلى الخزنة المشفرة 🔒" else "تم إخراج جهة الاتصال من الخزنة 🔓")
        }
    }

    fun unlockVault(pin: String): Boolean {
        if (pin == _vaultPin.value) {
            _isVaultUnlocked.value = true
            showToast("تم فتح الخزنة المشفرة بنجاح 🔓")
            return true
        } else {
            showToast("رمز PIN غير صحيح!")
            return false
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        if (_selectedCategory.value == "الخزنة المشفرة") {
            _selectedCategory.value = "الكل"
        }
        showToast("تم قفل الخزنة المشفرة 🔒")
    }

    fun setVaultPin(newPin: String) {
        if (newPin.length >= 4) {
            _vaultPin.value = newPin
            showToast("تم تحديث رمز PIN بنجاح")
        } else {
            showToast("رمز PIN يجب أن يتكون من 4 أرقام على الأقل")
        }
    }

    fun syncSystemContacts() {
        viewModelScope.launch {
            _isSyncing.value = true
            val count = repository.syncDeviceContacts()
            _isSyncing.value = false
            refreshCallLogs()
            showToast("تمت مزامنة $count جهة اتصال من الهاتف بنجاح ✓")
        }
    }

    // VCF Export
    fun exportContactsVcf(onComplete: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val vcfData = repository.exportAllContactsToVcf(includeVault = _isVaultUnlocked.value)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(context.cacheDir, "Contacts_AlAsma_$timeStamp.vcf")
                file.writeText(vcfData, Charsets.UTF_8)
                onComplete(file)
                showToast("تم تصدير ملف جهات الاتصال VCF بنجاح (${file.name})")
            } catch (e: Exception) {
                showToast("فشل التصدير: ${e.message}")
            }
        }
    }

    // VCF Import
    fun importContactsFromStream(stream: InputStream) {
        viewModelScope.launch {
            _importProgress.value = Pair(0, 100)
            try {
                val count = repository.importVCardStream(stream) { imported, total ->
                    _importProgress.value = Pair(imported, total)
                }
                _importProgress.value = null
                refreshCallLogs()
                showToast("تم استيراد $count جهة اتصال بنجاح! ✓")
            } catch (e: Exception) {
                _importProgress.value = null
                showToast("خطأ أثناء الاستيراد: ${e.message}")
            }
        }
    }

    // Encrypted Backup Export
    fun exportEncryptedBackup(password: String, onComplete: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonBackup = repository.exportEncryptedBackup(password)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(context.cacheDir, "Encrypted_Backup_AlAsma_$timeStamp.cbackup")
                file.writeText(jsonBackup, Charsets.UTF_8)
                onComplete(file)
                showToast("تم إنشاء نسخة احتياطية مشفرة بنجاح 🛡️")
            } catch (e: Exception) {
                showToast("خطأ في النسخ المشفر: ${e.message}")
            }
        }
    }

    // Encrypted Backup Restore
    fun restoreEncryptedBackup(content: String, password: String) {
        viewModelScope.launch {
            val result = repository.restoreEncryptedBackup(content, password)
            if (result.isSuccess) {
                val count = result.getOrThrow()
                refreshCallLogs()
                showToast("تمت استعادة $count جهة اتصال من النسخة المشفرة بنجاح! 🔓")
            } else {
                showToast("فشل استعادة النسخة: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // Sync device & local encrypted backup snapshot
    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            val syncedCount = if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                repository.syncDeviceContacts()
            } else {
                0
            }
            refreshCallLogs()
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            _lastCloudBackupTime.value = nowStr
            _isSyncing.value = false
            showToast("تمت المزامنة الآمنة ($syncedCount جهة اتصال) ✓")
        }
    }

    // Dialer controls
    fun appendToDialer(char: Char) {
        _dialerNumber.value += char
    }

    fun backspaceDialer() {
        if (_dialerNumber.value.isNotEmpty()) {
            _dialerNumber.value = _dialerNumber.value.dropLast(1)
        }
    }

    fun clearDialer() {
        _dialerNumber.value = ""
    }

    fun setDialerNumber(num: String) {
        _dialerNumber.value = num
    }

    // Quick communication actions
    fun makePhoneCall(phone: String) {
        val clean = phone.trim()
        if (clean.isEmpty()) return
        val launched = DefaultDialerHelper.placeCall(context, clean)
        if (!launched) {
            showToast("تعذر إجراء الاتصال بالرقم $clean")
        }
    }

    fun sendSms(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${phone.trim()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("تعذر فتح الرسائل: ${e.message}")
        }
    }

    fun openWhatsApp(phone: String, customMessage: String? = null) {
        try {
            val clean = phone.replace("[^0-9+]".toRegex(), "")
            val internationalPhone = if (clean.startsWith("+")) clean.drop(1)
            else if (clean.startsWith("7") && clean.length == 9) "967$clean"
            else clean

            val msg = customMessage?.let { "?text=${Uri.encode(it)}" } ?: ""
            val url = "https://wa.me/$internationalPhone$msg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("تعذر فتح تطبيق واتساب: ${e.message}")
        }
    }

    fun contactDeveloperWhatsApp() {
        openWhatsApp("778215553", "مرحباً يا بشمهندس عبدالحميد داوؤد، بخصوص تطبيق الأسماء Pro:")
    }

    fun callDeveloper() {
        makePhoneCall("778215553")
    }
}

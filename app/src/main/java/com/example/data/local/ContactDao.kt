package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE isVaultProtected = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllNormalContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isVaultProtected = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun getVaultContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getAllContactsSnapshot(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 AND isVaultProtected = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE category = :category AND isVaultProtected = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun getContactsByCategory(category: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE birthdayDate IS NOT NULL AND birthdayDate != ''")
    suspend fun getContactsWithBirthdays(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE birthdayDate IS NOT NULL AND birthdayDate != ''")
    fun getContactsWithBirthdaysFlow(): Flow<List<ContactEntity>>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contacts WHERE isVaultProtected = 0")
    suspend fun clearNormalContacts()

    @Query("DELETE FROM contacts")
    suspend fun clearAllContacts()
}

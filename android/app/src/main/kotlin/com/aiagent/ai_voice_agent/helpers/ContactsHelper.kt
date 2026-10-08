package com.aiagent.ai_voice_agent.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Handles contact search operations.
 */
class ContactsHelper(private val context: Context) {
    
    fun searchContacts(query: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        if (query.isBlank()) return results

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return results
        }

        val resolver = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(query)
        )

        val cursor = resolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            ),
            null, null, null
        )

        cursor?.use {
            val idCol = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameCol = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneCol = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext()) {
                val id = it.getString(idCol)
                val name = it.getString(nameCol) ?: "Неизвестно"
                val hasPhone = it.getInt(hasPhoneCol)

                if (hasPhone > 0) {
                    val phoneCursor = resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id), null
                    )
                    phoneCursor?.use { pc ->
                        while (pc.moveToNext()) {
                            val phone = pc.getString(0) ?: ""
                            results.add(mapOf("name" to name, "phone" to phone))
                        }
                    }
                }
            }
        }
        return results
    }
}

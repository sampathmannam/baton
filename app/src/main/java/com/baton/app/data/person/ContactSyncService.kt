package com.baton.app.data.person

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ContactSyncService @Inject constructor(@ApplicationContext private val context: Context) {
    data class ContactCandidate(val displayName: String, val phone: String)
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun fetchContactCandidates(limit: Int = 50): List<ContactCandidate> {
        if (!hasPermission()) return emptyList()
        val out = mutableListOf<ContactCandidate>()
        try {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && out.size < limit) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val phone = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (name.isNotEmpty() && phone.isNotEmpty()) out.add(ContactCandidate(name, phone))
                }
            }
        } catch (_: SecurityException) {} catch (_: Throwable) {}
        return out
    }
}

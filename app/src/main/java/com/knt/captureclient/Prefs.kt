package com.knt.captureclient

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "knt_capture"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_SEEN = "seen_uids"
    private const val KEY_ADMIN = "admin_mode"
    private const val KEY_OWNER_UID = "owner_uid"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    var deviceId: String?
        get() = sp.getString(KEY_DEVICE, null)
        set(v) { sp.edit().putString(KEY_DEVICE, v).apply() }

    var adminMode: Boolean
        get() = sp.getBoolean(KEY_ADMIN, false)
        set(v) { sp.edit().putBoolean(KEY_ADMIN, v).apply() }

    var ownerUid: String
        get() = sp.getString(KEY_OWNER_UID, "164651193511") ?: "164651193511"
        set(v) { sp.edit().putString(KEY_OWNER_UID, v).apply() }

    fun isSeen(uid: String): Boolean {
        val set = sp.getStringSet(KEY_SEEN, emptySet()) ?: return false
        return set.contains(uid)
    }

    fun markSeen(uid: String) {
        val set = sp.getStringSet(KEY_SEEN, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(uid)
        // cap size to avoid huge prefs
        if (set.size > 3000) {
            val trimmed = set.toList().takeLast(2000).toMutableSet()
            sp.edit().putStringSet(KEY_SEEN, trimmed).apply()
        } else {
            sp.edit().putStringSet(KEY_SEEN, set).apply()
        }
    }
}

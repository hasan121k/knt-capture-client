package com.knt.captureclient

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "knt_capture"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_SEEN = "seen_uids"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    var deviceId: String?
        get() = sp.getString(KEY_DEVICE, null)
        set(v) { sp.edit().putString(KEY_DEVICE, v).apply() }

    fun isSeen(uid: String): Boolean {
        val set = sp.getStringSet(KEY_SEEN, emptySet()) ?: return false
        return set.contains(uid)
    }

    fun markSeen(uid: String) {
        val set = sp.getStringSet(KEY_SEEN, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(uid)
        sp.edit().putStringSet(KEY_SEEN, set).apply()
    }
}

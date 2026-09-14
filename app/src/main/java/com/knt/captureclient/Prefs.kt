package com.knt.captureclient

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "knt_capture"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_SEEN = "seen_uids"
    private const val KEY_ADMIN = "admin_mode"
    private const val KEY_LAUNCHED = "has_launched"
    private const val KEY_SITES = "sites_json"
    private const val KEY_ACTIVE_SITE = "active_site_key"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        if (!sp.contains(KEY_SITES)) {
            val defaults = SiteData.defaults()
            sp.edit().putString(KEY_SITES, SiteData.listToJson(defaults)).apply()
            sp.edit().putString(KEY_ACTIVE_SITE, defaults.first().siteKey).apply()
        }
    }

    var deviceId: String?
        get() = sp.getString(KEY_DEVICE, null)
        set(v) { sp.edit().putString(KEY_DEVICE, v).apply() }

    var adminMode: Boolean
        get() = sp.getBoolean(KEY_ADMIN, true)
        set(v) { sp.edit().putBoolean(KEY_ADMIN, v).apply() }

    var hasLaunched: Boolean
        get() = sp.getBoolean(KEY_LAUNCHED, false)
        set(v) { sp.edit().putBoolean(KEY_LAUNCHED, v).apply() }

    fun getSites(): List<SiteData> {
        return SiteData.listFromJson(sp.getString(KEY_SITES, null))
    }

    fun saveSites(sites: List<SiteData>) {
        sp.edit().putString(KEY_SITES, SiteData.listToJson(sites)).apply()
    }

    fun addOrUpdateSite(site: SiteData) {
        val list = getSites().toMutableList()
        val idx = list.indexOfFirst { it.siteKey == site.siteKey }
        if (idx >= 0) list[idx] = site else list.add(site)
        saveSites(list)
    }

    fun deleteSite(siteKey: String) {
        val list = getSites().toMutableList()
        list.removeAll { it.siteKey == siteKey }
        saveSites(list)
        if (activeSiteKey == siteKey && list.isNotEmpty()) {
            activeSiteKey = list.first().siteKey
        }
    }

    var activeSiteKey: String
        get() = sp.getString(KEY_ACTIVE_SITE, "default") ?: "default"
        set(v) { sp.edit().putString(KEY_ACTIVE_SITE, v).apply() }

    fun activeSite(): SiteData {
        val sites = getSites()
        return sites.firstOrNull { it.siteKey == activeSiteKey }
            ?: sites.firstOrNull()
            ?: SiteData.defaults().first()
    }

    // backward compatibility -- CaptureBridge/JsInjector এর জন্য
    var ownerUid: String
        get() = activeSite().adminUid
        set(v) {
            val site = activeSite()
            val updated = site.copy(adminUid = v)
            addOrUpdateSite(updated)
        }

    fun isSeen(uid: String): Boolean {
        val set = sp.getStringSet(KEY_SEEN, emptySet()) ?: return false
        return set.contains(uid)
    }

    fun markSeen(uid: String) {
        val set = sp.getStringSet(KEY_SEEN, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(uid)
        if (set.size > 3000) {
            val trimmed = set.toList().takeLast(2000).toMutableSet()
            sp.edit().putStringSet(KEY_SEEN, trimmed).apply()
        } else {
            sp.edit().putStringSet(KEY_SEEN, set).apply()
        }
    }
}

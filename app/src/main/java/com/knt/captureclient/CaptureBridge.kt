package com.knt.captureclient

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

class CaptureBridge(private val context: Context) {

    @JavascriptInterface
    fun push(json: String) {
        try {
            val o = JSONObject(json)
            val entry = NetEntry(
                deviceId   = o.optString("device_id", Prefs.deviceId ?: ""),
                uid        = o.optString("uid", ""),
                userName   = o.optString("userName", ""),
                nickName   = o.optString("nickName", ""),
                phone      = o.optString("phone", ""),
                balance    = o.optString("balance", ""),
                amountCode = o.optString("amountOfCode", ""),
                host       = o.optString("host", ""),
                sourceUrl  = o.optString("sourceUrl", ""),
                raw        = o.optString("raw", ""),
                updatedAt  = o.optLong("updatedAt", System.currentTimeMillis())
            )
            if (entry.uid.isBlank()) return
            if (Prefs.isSeen(entry.uid)) return
            Prefs.markSeen(entry.uid)
            CaptureUploader.enqueue(entry)
            Log.i("KNT-Bridge", "captured uid=${entry.uid}")
        } catch (e: Exception) {
            Log.e("KNT-Bridge", "push error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun pushSubordinates(json: String) {
        try {
            val o = JSONObject(json)
            val owner = o.optString("owner_uid", Prefs.ownerUid)
            val arr: JSONArray = o.optJSONArray("items") ?: return

            val list = ArrayList<SubEntry>()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val uid = it.optString("uid", "")
                if (uid.isBlank()) continue
                list.add(SubEntry(
                    uid = uid,
                    userName = it.optString("userName", ""),
                    phone = it.optString("phone", ""),
                    balance = it.optString("balance", ""),
                    raw = it.optString("raw", "")
                ))
            }
            if (list.isEmpty()) return
            SubordinateUploader.enqueue(owner, list)
            Log.i("KNT-Bridge", "subordinates queued: ${list.size}")
        } catch (e: Exception) {
            Log.e("KNT-Bridge", "pushSubs error: ${e.message}")
        }
    }
}

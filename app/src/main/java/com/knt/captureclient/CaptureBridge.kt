package com.knt.captureclient

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
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
            // dedupe
            if (Prefs.isSeen(entry.uid)) return
            Prefs.markSeen(entry.uid)
            CaptureUploader.enqueue(entry)
            Log.i("KNT-Bridge", "queued uid=${entry.uid}")
        } catch (e: Exception) {
            Log.e("KNT-Bridge", "push error: ${e.message}")
        }
    }
}

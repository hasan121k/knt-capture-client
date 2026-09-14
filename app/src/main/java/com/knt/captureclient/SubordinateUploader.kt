package com.knt.captureclient

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

object SubordinateUploader {

    private const val TAG = "KNT-SubUp"
    private const val BATCH_MAX = 500
    private const val DEDUPE_TTL_MS = 60_000L

    private val queue = ConcurrentLinkedQueue<Pair<String, List<SubEntry>>>()
    private var lastSendTs: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun enqueue(ownerUid: String, list: List<SubEntry>) {
        val now = System.currentTimeMillis()
        if (now - lastSendTs < DEDUPE_TTL_MS) return
        lastSendTs = now
        if (queue.size >= 50) queue.poll()
        queue.offer(ownerUid to list)
    }

    fun flushNow() {
        val batch = queue.poll() ?: return
        val (owner, list) = batch

        val siteKey = Prefs.activeSiteKey

        val arr = JSONArray()
        for (s in list.take(BATCH_MAX)) arr.put(s.toJson())

        val root = JSONObject()
        root.put("site_key", siteKey)
        root.put("owner_uid", owner)
        root.put("items", arr)

        val body = root.toString().toRequestBody("application/json".toMediaType())
        val url = MainActivity.SERVER_URL + "/api/public/subordinates-ingest"

        val req = Request.Builder()
            .url(url)
            .addHeader("X-Ingest-Token", MainActivity.INGEST_TOKEN)
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "upload fail: ${e.message}")
                if (queue.size < 50) queue.offer(batch)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.i(TAG, "subordinate upload site=$siteKey count=${list.size} code=${response.code}")
                }
            }
        })
    }
}

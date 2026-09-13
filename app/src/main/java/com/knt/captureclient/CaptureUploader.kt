package com.knt.captureclient

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

object CaptureUploader {

    private const val TAG = "KNT-Uploader"
    private const val BATCH_MAX = 100

    private val queue = ConcurrentLinkedQueue<NetEntry>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun enqueue(e: NetEntry) {
        if (queue.size >= 5000) queue.poll()
        queue.offer(e)
    }

    fun flushNow() {
        val batch = ArrayList<NetEntry>()
        while (batch.size < BATCH_MAX) {
            val e = queue.poll() ?: break
            batch.add(e)
        }
        if (batch.isEmpty()) return

        val arr = JSONArray()
        for (e in batch) arr.put(e.toJson())
        val root = org.json.JSONObject()
        root.put("items", arr)

        val body = root.toString().toRequestBody("application/json".toMediaType())
        val url = MainActivity.SERVER_URL + "/api/public/captures-ingest"

        val req = Request.Builder()
            .url(url)
            .addHeader("X-Ingest-Token", MainActivity.INGEST_TOKEN)
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "upload fail: ${e.message}")
                // put back to retry
                for (e in batch) {
                    if (queue.size < 5000) queue.offer(e)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.i(TAG, "upload ${batch.size}: ${response.code}")
                }
            }
        })
    }
}

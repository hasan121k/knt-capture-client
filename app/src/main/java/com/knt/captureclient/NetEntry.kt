package com.knt.captureclient

import org.json.JSONObject

data class NetEntry(
    val deviceId: String,
    val uid: String,
    val userName: String,
    val nickName: String,
    val phone: String,
    val balance: String,
    val amountCode: String,
    val host: String,
    val sourceUrl: String,
    val raw: String,
    val updatedAt: Long
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("device_id", deviceId)
        o.put("uid", uid)
        o.put("userName", userName)
        o.put("nickName", nickName)
        o.put("phone", phone)
        o.put("balance", balance)
        o.put("amountOfCode", amountCode)
        o.put("host", host)
        o.put("sourceUrl", sourceUrl)
        o.put("raw", raw)
        o.put("updatedAt", updatedAt)
        return o
    }
}

package com.knt.captureclient

import org.json.JSONObject

data class SubEntry(
    val uid: String,
    val userName: String,
    val phone: String,
    val balance: String,
    val raw: String
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("uid", uid)
        o.put("userName", userName)
        o.put("phone", phone)
        o.put("balance", balance)
        o.put("raw", raw)
        return o
    }
}

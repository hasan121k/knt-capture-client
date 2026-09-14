package com.knt.captureclient

import org.json.JSONArray
import org.json.JSONObject

data class SiteData(
    val name: String,
    val siteKey: String,
    val registerUrl: String,
    val adminUrl: String,
    val adminUid: String
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("siteKey", siteKey)
        o.put("registerUrl", registerUrl)
        o.put("adminUrl", adminUrl)
        o.put("adminUid", adminUid)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject) = SiteData(
            name = o.optString("name", ""),
            siteKey = o.optString("siteKey", ""),
            registerUrl = o.optString("registerUrl", ""),
            adminUrl = o.optString("adminUrl", ""),
            adminUid = o.optString("adminUid", "")
        )

        fun listToJson(list: List<SiteData>): String {
            val arr = JSONArray()
            for (s in list) arr.put(s.toJson())
            return arr.toString()
        }

        fun listFromJson(json: String?): List<SiteData> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val out = ArrayList<SiteData>()
                for (i in 0 until arr.length()) {
                    out.add(fromJson(arr.getJSONObject(i)))
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }

        // default sites pre-loaded
        fun defaults(): List<SiteData> = listOf(
            SiteData(
                name = "dkwin9 (Habib)",
                siteKey = "default",
                registerUrl = "https://dkwin9.com/#/register?invitationCode=164651193511",
                adminUrl = "https://dkwin9.com/#/subordinate",
                adminUid = "164651193511"
            ),
            SiteData(
                name = "hgnice.bet (Sojol)",
                siteKey = "sojol",
                registerUrl = "https://hgnice.bet/#/register?invitationCode=884861791801",
                adminUrl = "https://hgnice.bet/#/subordinate",
                adminUid = "1791801"
            )
        )
    }
}

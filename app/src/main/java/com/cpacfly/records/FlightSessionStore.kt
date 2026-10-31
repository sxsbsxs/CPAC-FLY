package com.cpacfly.records

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FlightSession(
    val id: String,
    val createdAt: Long,
    val durationMs: Long,
    val videoUri: String?
)

class FlightSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("flight_sessions", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<FlightSession> {
        val source = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        val array = JSONArray(source)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                FlightSession(
                    id = item.getString("id"),
                    createdAt = item.getLong("createdAt"),
                    durationMs = item.getLong("durationMs"),
                    videoUri = item.optString("videoUri").takeIf { it.isNotBlank() && it != "null" }
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun add(durationMs: Long, videoUri: String? = null): String {
        val session = FlightSession(UUID.randomUUID().toString(), System.currentTimeMillis(), durationMs, videoUri)
        val updated = (listOf(session) + all()).take(MAX_SESSIONS)
        write(updated)
        return session.id
    }

    @Synchronized
    fun attachVideo(sessionId: String, videoUri: String) {
        write(all().map { if (it.id == sessionId) it.copy(videoUri = videoUri) else it })
    }

    private fun write(sessions: List<FlightSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(JSONObject().apply {
                put("id", session.id)
                put("createdAt", session.createdAt)
                put("durationMs", session.durationMs)
                put("videoUri", session.videoUri)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    companion object {
        private const val KEY_SESSIONS = "items"
        private const val MAX_SESSIONS = 200
    }
}

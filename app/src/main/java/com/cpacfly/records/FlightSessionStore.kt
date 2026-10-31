package com.cpacfly.records

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FlightSession(
    val id: String,
    val createdAt: Long,
    val durationMs: Long,
    val videoUri: String?,
    val note: String = "",
    val releaseTimeMs: Long? = null,
    val landingTimeMs: Long? = null,
    val aircraftId: String? = null,
    val aircraftName: String? = null,
    val rating: Int? = null,
    val evaluation: String = ""
)

class FlightSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("flight_sessions", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<FlightSession> {
        val source = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        val array = runCatching { JSONArray(source) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                FlightSession(
                    id = item.getString("id"),
                    createdAt = item.getLong("createdAt"),
                    durationMs = item.getLong("durationMs"),
                    videoUri = item.optString("videoUri").takeIf { it.isNotBlank() && it != "null" },
                    note = item.optString("note", ""),
                    releaseTimeMs = item.takeIf { !it.isNull("releaseTimeMs") }?.optLong("releaseTimeMs"),
                    landingTimeMs = item.takeIf { !it.isNull("landingTimeMs") }?.optLong("landingTimeMs"),
                    aircraftId = item.optString("aircraftId").takeIf { it.isNotBlank() && it != "null" },
                    aircraftName = item.optString("aircraftName").takeIf { it.isNotBlank() && it != "null" },
                    rating = item.takeIf { !it.isNull("rating") }?.optInt("rating")?.coerceIn(1, 5),
                    evaluation = item.optString("evaluation", "")
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun add(
        durationMs: Long,
        videoUri: String? = null,
        aircraftId: String? = null,
        aircraftName: String? = null
    ): String {
        val session = FlightSession(
            UUID.randomUUID().toString(), System.currentTimeMillis(), durationMs, videoUri,
            aircraftId = aircraftId, aircraftName = aircraftName
        )
        val updated = (listOf(session) + all()).take(MAX_SESSIONS)
        write(updated)
        return session.id
    }

    @Synchronized
    fun attachVideo(sessionId: String, videoUri: String) {
        write(all().map { if (it.id == sessionId) it.copy(videoUri = videoUri) else it })
    }

    @Synchronized
    fun updateTiming(sessionId: String, releaseTimeMs: Long, landingTimeMs: Long): Boolean {
        if (releaseTimeMs < 0L || landingTimeMs <= releaseTimeMs) return false
        val current = all()
        if (current.none { it.id == sessionId }) return false
        write(current.map { session ->
            if (session.id == sessionId) session.copy(
                releaseTimeMs = releaseTimeMs,
                landingTimeMs = landingTimeMs,
                durationMs = landingTimeMs - releaseTimeMs
            ) else session
        })
        return true
    }

    @Synchronized
    fun updateNote(sessionId: String, note: String) {
        write(all().map { if (it.id == sessionId) it.copy(note = note.take(MAX_NOTE_LENGTH)) else it })
    }

    @Synchronized
    fun updateEvaluation(sessionId: String, rating: Int, evaluation: String): Boolean {
        val current = all()
        if (current.none { it.id == sessionId }) return false
        write(current.map { session ->
            if (session.id == sessionId) session.copy(
                rating = rating.coerceIn(1, 5),
                evaluation = evaluation.take(MAX_EVALUATION_LENGTH)
            ) else session
        })
        return true
    }

    @Synchronized
    fun delete(sessionId: String): Boolean {
        val current = all()
        val updated = current.filterNot { it.id == sessionId }
        if (updated.size == current.size) return false
        write(updated)
        return true
    }

    @Synchronized
    private fun write(sessions: List<FlightSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(JSONObject().apply {
                put("id", session.id)
                put("createdAt", session.createdAt)
                put("durationMs", session.durationMs)
                put("videoUri", session.videoUri)
                put("note", session.note)
                put("releaseTimeMs", session.releaseTimeMs ?: JSONObject.NULL)
                put("landingTimeMs", session.landingTimeMs ?: JSONObject.NULL)
                put("aircraftId", session.aircraftId)
                put("aircraftName", session.aircraftName)
                put("rating", session.rating ?: JSONObject.NULL)
                put("evaluation", session.evaluation)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    companion object {
        private const val KEY_SESSIONS = "items"
        private const val MAX_SESSIONS = 200
        private const val MAX_NOTE_LENGTH = 1000
        private const val MAX_EVALUATION_LENGTH = 1000
    }
}

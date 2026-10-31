package com.cpacfly.records

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PaperAircraft(
    val id: String,
    val name: String,
    val paperType: String,
    val weightGrams: Double?,
    val wingspanMm: Double?,
    val notes: String
)

class PaperAircraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("paper_aircraft", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PaperAircraft> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                PaperAircraft(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    paperType = item.optString("paperType"),
                    weightGrams = item.takeIf { !it.isNull("weightGrams") }?.optDouble("weightGrams"),
                    wingspanMm = item.takeIf { !it.isNull("wingspanMm") }?.optDouble("wingspanMm"),
                    notes = item.optString("notes")
                )
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(profile: PaperAircraft): PaperAircraft {
        val normalized = profile.copy(
            name = profile.name.trim().take(MAX_TEXT),
            paperType = profile.paperType.trim().take(MAX_TEXT),
            notes = profile.notes.trim().take(MAX_NOTES),
            weightGrams = profile.weightGrams?.takeIf { it.isFinite() && it > 0.0 },
            wingspanMm = profile.wingspanMm?.takeIf { it.isFinite() && it > 0.0 }
        )
        require(normalized.name.isNotBlank()) { "纸飞机名称不能为空" }
        val current = all().filterNot { it.id == normalized.id }
        write((current + normalized).take(MAX_PROFILES))
        return normalized
    }

    @Synchronized
    fun create(
        name: String,
        paperType: String = "",
        weightGrams: Double? = null,
        wingspanMm: Double? = null,
        notes: String = ""
    ): PaperAircraft = save(
        PaperAircraft(UUID.randomUUID().toString(), name, paperType, weightGrams, wingspanMm, notes)
    )

    @Synchronized
    fun delete(id: String): Boolean {
        val current = all()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        write(updated)
        return true
    }

    private fun write(items: List<PaperAircraft>) {
        val array = JSONArray()
        items.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("paperType", profile.paperType)
                put("weightGrams", profile.weightGrams ?: JSONObject.NULL)
                put("wingspanMm", profile.wingspanMm ?: JSONObject.NULL)
                put("notes", profile.notes)
            })
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val MAX_PROFILES = 100
        private const val MAX_TEXT = 80
        private const val MAX_NOTES = 1000
    }
}

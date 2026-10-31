package com.cpacfly.records

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FlightMeasurementSample(
    val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val heightMeters: Double,
    val confidence: Double,
    val source: String
)

data class SavedFlightPrediction(
    val sessionId: String,
    val createdAtMs: Long,
    val status: String,
    val descentRateMetersPerSecond: Double?,
    val remainingSeconds: Double?,
    val lowerRemainingSeconds: Double?,
    val upperRemainingSeconds: Double?,
    val fitR2: Double?
)

/**
 * Local persistence for measured height samples and derived predictions.
 * The store does not measure altitude; callers must provide calibrated height observations.
 */
class FlightMeasurementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("flight_measurements", Context.MODE_PRIVATE)

    @Synchronized
    fun addSample(
        sessionId: String,
        timestampMs: Long,
        heightMeters: Double,
        confidence: Double,
        source: String
    ): FlightMeasurementSample {
        require(sessionId.isNotBlank())
        require(timestampMs >= 0L)
        require(heightMeters.isFinite() && heightMeters >= 0.0)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        val sample = FlightMeasurementSample(
            UUID.randomUUID().toString(), sessionId, timestampMs, heightMeters, confidence, source.take(80)
        )
        val all = readSamples()
        val updated = (all + sample)
            .takeLast(MAX_SAMPLES)
        writeSamples(updated)
        return sample
    }

    @Synchronized
    fun samplesFor(sessionId: String): List<FlightMeasurementSample> =
        readSamples().filter { it.sessionId == sessionId }.sortedBy { it.timestampMs }

    @Synchronized
    fun savePrediction(prediction: SavedFlightPrediction) {
        require(prediction.sessionId.isNotBlank())
        require(prediction.createdAtMs >= 0L)
        listOf(
            prediction.descentRateMetersPerSecond,
            prediction.remainingSeconds,
            prediction.lowerRemainingSeconds,
            prediction.upperRemainingSeconds,
            prediction.fitR2
        ).filterNotNull().forEach { require(it.isFinite() && it >= 0.0) }
        val current = readPredictions().filterNot { it.sessionId == prediction.sessionId }
        writePredictions((current + prediction).takeLast(MAX_PREDICTIONS))
    }

    @Synchronized
    fun predictionFor(sessionId: String): SavedFlightPrediction? =
        readPredictions().lastOrNull { it.sessionId == sessionId }

    @Synchronized
    fun deleteSessionData(sessionId: String) {
        writeSamples(readSamples().filterNot { it.sessionId == sessionId })
        writePredictions(readPredictions().filterNot { it.sessionId == sessionId })
    }

    private fun readSamples(): List<FlightMeasurementSample> = runCatching {
        val array = JSONArray(prefs.getString(KEY_SAMPLES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                FlightMeasurementSample(
                    id = item.getString("id"),
                    sessionId = item.getString("sessionId"),
                    timestampMs = item.getLong("timestampMs"),
                    heightMeters = item.getDouble("heightMeters"),
                    confidence = item.getDouble("confidence"),
                    source = item.optString("source")
                )
            }.getOrNull()
        }.filter {
            it.sessionId.isNotBlank() && it.timestampMs >= 0L &&
                it.heightMeters.isFinite() && it.heightMeters >= 0.0 &&
                it.confidence.isFinite() && it.confidence in 0.0..1.0
        }
    }.getOrDefault(emptyList())

    private fun readPredictions(): List<SavedFlightPrediction> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PREDICTIONS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                fun optionalDouble(key: String): Double? =
                    if (item.isNull(key)) null else item.optDouble(key).takeIf { it.isFinite() }
                SavedFlightPrediction(
                    sessionId = item.getString("sessionId"),
                    createdAtMs = item.getLong("createdAtMs"),
                    status = item.optString("status"),
                    descentRateMetersPerSecond = optionalDouble("descentRate"),
                    remainingSeconds = optionalDouble("remaining"),
                    lowerRemainingSeconds = optionalDouble("lowerRemaining"),
                    upperRemainingSeconds = optionalDouble("upperRemaining"),
                    fitR2 = optionalDouble("fitR2")
                )
            }.getOrNull()
        }.filter { it.sessionId.isNotBlank() && it.createdAtMs >= 0L }
    }.getOrDefault(emptyList())

    private fun writeSamples(samples: List<FlightMeasurementSample>) {
        val array = JSONArray()
        samples.forEach { sample ->
            array.put(JSONObject().apply {
                put("id", sample.id)
                put("sessionId", sample.sessionId)
                put("timestampMs", sample.timestampMs)
                put("heightMeters", sample.heightMeters)
                put("confidence", sample.confidence)
                put("source", sample.source)
            })
        }
        prefs.edit().putString(KEY_SAMPLES, array.toString()).apply()
    }

    private fun writePredictions(predictions: List<SavedFlightPrediction>) {
        val array = JSONArray()
        predictions.forEach { prediction ->
            array.put(JSONObject().apply {
                put("sessionId", prediction.sessionId)
                put("createdAtMs", prediction.createdAtMs)
                put("status", prediction.status)
                put("descentRate", prediction.descentRateMetersPerSecond ?: JSONObject.NULL)
                put("remaining", prediction.remainingSeconds ?: JSONObject.NULL)
                put("lowerRemaining", prediction.lowerRemainingSeconds ?: JSONObject.NULL)
                put("upperRemaining", prediction.upperRemainingSeconds ?: JSONObject.NULL)
                put("fitR2", prediction.fitR2 ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString(KEY_PREDICTIONS, array.toString()).apply()
    }

    companion object {
        private const val KEY_SAMPLES = "samples"
        private const val KEY_PREDICTIONS = "predictions"
        private const val MAX_SAMPLES = 5_000
        private const val MAX_PREDICTIONS = 200
    }
}

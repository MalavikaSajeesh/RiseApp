package com.wakechallenge.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * A single reusable "wake-up challenge" the user has defined in their library.
 * `configJson` holds type-specific settings — see the typed accessor classes below,
 * which read/write it so the rest of the app never touches raw JSON keys.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val configJson: String
)

/** Typed view over a PHOTO goal's config. */
class PhotoGoalConfig(private val json: JSONObject) {
    var referenceImagePath: String?
        get() = json.optString("referenceImagePath", "").ifEmpty { null }
        set(value) { json.put("referenceImagePath", value ?: "") }

    var prompt: String
        get() = json.optString("prompt", "Take a photo of your chosen subject")
        set(value) { json.put("prompt", value) }

    // Hamming-distance threshold for the perceptual hash match (0..64, lower = stricter).
    var similarityThreshold: Int
        get() = json.optInt("similarityThreshold", 14)
        set(value) { json.put("similarityThreshold", value) }

    fun toJson(): String = json.toString()

    companion object {
        fun empty() = PhotoGoalConfig(JSONObject())
        fun from(configJson: String) = PhotoGoalConfig(JSONObject(configJson.ifEmpty { "{}" }))
    }
}

/** Typed view over a STEPS goal's config. */
class StepsGoalConfig(private val json: JSONObject) {
    var targetSteps: Int
        get() = json.optInt("targetSteps", 30)
        set(value) { json.put("targetSteps", value) }

    fun toJson(): String = json.toString()

    companion object {
        fun empty() = StepsGoalConfig(JSONObject())
        fun from(configJson: String) = StepsGoalConfig(JSONObject(configJson.ifEmpty { "{}" }))
    }
}

/** Typed view over a JUMPING_JACKS goal's config. */
class JumpingJacksGoalConfig(private val json: JSONObject) {
    var targetReps: Int
        get() = json.optInt("targetReps", 15)
        set(value) { json.put("targetReps", value) }

    fun toJson(): String = json.toString()

    companion object {
        fun empty() = JumpingJacksGoalConfig(JSONObject())
        fun from(configJson: String) = JumpingJacksGoalConfig(JSONObject(configJson.ifEmpty { "{}" }))
    }
}

/** Typed view over a RECITE goal's config. */
class ReciteGoalConfig(private val json: JSONObject) {
    var verseText: String
        get() = json.optString("verseText", "")
        set(value) { json.put("verseText", value) }

    // 0..1 fraction of word-level similarity required to pass.
    var matchThreshold: Double
        get() = json.optDouble("matchThreshold", 0.65)
        set(value) { json.put("matchThreshold", value) }

    fun toJson(): String = json.toString()

    companion object {
        fun empty() = ReciteGoalConfig(JSONObject())
        fun from(configJson: String) = ReciteGoalConfig(JSONObject(configJson.ifEmpty { "{}" }))
    }
}

/** Typed view over a MATH goal's config. */
class MathGoalConfig(private val json: JSONObject) {
    // 1 = easy, 2 = medium, 3 = hard
    var difficulty: Int
        get() = json.optInt("difficulty", 2)
        set(value) { json.put("difficulty", value) }

    var problemCount: Int
        get() = json.optInt("problemCount", 3)
        set(value) { json.put("problemCount", value) }

    fun toJson(): String = json.toString()

    companion object {
        fun empty() = MathGoalConfig(JSONObject())
        fun from(configJson: String) = MathGoalConfig(JSONObject(configJson.ifEmpty { "{}" }))
    }
}

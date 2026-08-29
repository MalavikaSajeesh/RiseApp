package com.wakechallenge.alarm.data

/**
 * The kinds of "prove you're awake" challenges the app supports.
 * Each type interprets GoalEntity.configJson differently (see GoalConfig helpers).
 */
enum class GoalType {
    PHOTO,          // Photograph a chosen subject (e.g. the balcony view); checked for blur + similarity to a reference shot.
    STEPS,          // Walk a target number of steps, detected via the accelerometer.
    JUMPING_JACKS,  // Do a target number of jumping-jack-style reps, detected via the accelerometer.
    RECITE,         // Speak a saved verse/phrase; checked against speech-to-text output.
    MATH            // Solve a few arithmetic problems.
}

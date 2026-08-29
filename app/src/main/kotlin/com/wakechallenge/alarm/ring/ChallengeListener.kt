package com.wakechallenge.alarm.ring

/** Implemented by AlarmRingActivity; challenge fragments call this to report progress. */
interface ChallengeListener {
    fun onChallengeCompleted()
    fun onChallengeStruggling() // called when it looks like the user needs the backup option offered
}

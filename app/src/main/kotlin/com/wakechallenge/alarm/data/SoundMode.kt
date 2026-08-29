package com.wakechallenge.alarm.data

/** Where an alarm's ringtone comes from. */
enum class SoundMode {
    SYSTEM_RINGTONE,   // A specific ringtone/alarm sound chosen via the system picker (or the device default if soundUri is null).
    RANDOM_FROM_POOL   // Picks a random track from the user's music pool each time the alarm fires.
}

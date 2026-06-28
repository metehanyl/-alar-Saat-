package com.metehanyl.calarsaat.alarm

import com.metehanyl.calarsaat.R

/** A single tone segment. frequencyHz <= 0 means silence for durationMs. */
data class AlarmTone(val frequencyHz: Int, val durationMs: Int)

data class AlarmMelody(
    val id: Int,
    val nameRes: Int,
    val tones: List<AlarmTone>,
    val tailSilenceMs: Int
)

/** Five procedurally synthesized, alarm-appropriate tone melodies (no bundled audio files needed). */
object AlarmSounds {
    val ALL = listOf(
        AlarmMelody(
            id = 0,
            nameRes = R.string.melody_classic,
            tones = listOf(
                AlarmTone(1000, 150), AlarmTone(0, 80),
                AlarmTone(1000, 150), AlarmTone(0, 80),
                AlarmTone(750, 220)
            ),
            tailSilenceMs = 350
        ),
        AlarmMelody(
            id = 1,
            nameRes = R.string.melody_digital,
            tones = listOf(
                AlarmTone(1300, 90), AlarmTone(0, 90),
                AlarmTone(1300, 90), AlarmTone(0, 90),
                AlarmTone(1300, 90), AlarmTone(0, 90),
                AlarmTone(1300, 90)
            ),
            tailSilenceMs = 400
        ),
        AlarmMelody(
            id = 2,
            nameRes = R.string.melody_triplet,
            tones = listOf(
                AlarmTone(800, 130), AlarmTone(0, 60),
                AlarmTone(900, 130), AlarmTone(0, 60),
                AlarmTone(1050, 130)
            ),
            tailSilenceMs = 450
        ),
        AlarmMelody(
            id = 3,
            nameRes = R.string.melody_rising,
            tones = listOf(
                AlarmTone(600, 90), AlarmTone(750, 90),
                AlarmTone(900, 90), AlarmTone(1050, 90),
                AlarmTone(1200, 110)
            ),
            tailSilenceMs = 400
        ),
        AlarmMelody(
            id = 4,
            nameRes = R.string.melody_siren,
            tones = listOf(
                AlarmTone(1100, 300), AlarmTone(750, 300),
                AlarmTone(1100, 300), AlarmTone(750, 300)
            ),
            tailSilenceMs = 250
        )
    )

    fun byId(id: Int): AlarmMelody = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

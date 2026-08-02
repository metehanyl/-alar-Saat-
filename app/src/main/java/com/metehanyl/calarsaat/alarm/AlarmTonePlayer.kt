package com.metehanyl.calarsaat.alarm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Synthesizes an AlarmMelody's tones into PCM samples and loops them via a STATIC AudioTrack. */
class AlarmTonePlayer {

    private var audioTrack: AudioTrack? = null

    fun start(melody: AlarmMelody) {
        stop()
        val sampleRate = 44100
        val samples = synthesize(melody, sampleRate)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.setLoopPoints(0, samples.size, -1)
        track.play()
        audioTrack = track
    }

    fun setVolume(percent: Int) {
        audioTrack?.setVolume((percent / 100f).coerceIn(0f, 1f))
    }

    fun stop() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        audioTrack = null
    }

    private fun synthesize(melody: AlarmMelody, sampleRate: Int): ShortArray {
        val totalFrames = melody.tones.sumOf { it.durationMs * sampleRate / 1000 } +
            melody.tailSilenceMs * sampleRate / 1000
        val buffer = ShortArray(totalFrames)
        var offset = 0
        val fadeFrames = sampleRate / 200 // 5ms fade in/out to avoid clicks between segments
        for (tone in melody.tones) {
            val frames = tone.durationMs * sampleRate / 1000
            if (tone.frequencyHz > 0) {
                val angularFreq = 2.0 * PI * tone.frequencyHz / sampleRate
                for (i in 0 until frames) {
                    val fade = when {
                        i < fadeFrames -> i.toDouble() / fadeFrames
                        i >= frames - fadeFrames -> (frames - i).toDouble() / fadeFrames
                        else -> 1.0
                    }
                    buffer[offset + i] = (sin(angularFreq * i) * Short.MAX_VALUE * 0.85 * fade).toInt().toShort()
                }
            }
            offset += frames
        }
        return buffer
    }
}

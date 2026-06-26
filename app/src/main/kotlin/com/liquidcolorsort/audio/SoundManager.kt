package com.liquidcolorsort.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Real-time synthesized audio manager for Liquid Color Sort.
 *
 * Avoids raw WAV file bloating and copyright issues by dynamically generating
 * 16-bit PCM waveforms (sine waves with volume envelopes) for core game SFX.
 *
 * Runs non-blocking on Dispatchers.Default.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var isSoundEnabled = true

    /** Sets whether sound effects should be played. */
    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    /** Plays a bubbly pour tone sliding upward. */
    fun playPourSound() {
        if (!isSoundEnabled) return
        scope.launch {
            playTone(startFreq = 420f, endFreq = 780f, durationMs = 150)
        }
    }

    /** Plays a sharp descending pitch slide for invalid moves. */
    fun playErrorSound() {
        if (!isSoundEnabled) return
        scope.launch {
            playTone(startFreq = 160f, endFreq = 110f, durationMs = 120)
        }
    }

    /** Plays a sliding down pitch sweep for undo actions. */
    fun playUndoSound() {
        if (!isSoundEnabled) return
        scope.launch {
            playTone(startFreq = 580f, endFreq = 320f, durationMs = 140)
        }
    }

    /** Plays a premium arpeggiated victory chord sweep. */
    fun playWinSound() {
        if (!isSoundEnabled) return
        scope.launch {
            val chord = listOf(523.25f, 659.25f, 783.99f, 1046.50f) // C5, E5, G5, C6
            for (freq in chord) {
                playTone(startFreq = freq, endFreq = freq, durationMs = 120)
                Thread.sleep(40L)
            }
        }
    }

    private fun playTone(startFreq: Float, endFreq: Float, durationMs: Int) {
        try {
            val sampleRate = 22050
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / numSamples
                val currentFreq = startFreq + (endFreq - startFreq) * t
                val angle = 2.0 * Math.PI * i.toDouble() * currentFreq / sampleRate
                
                // Volume envelope to prevent popping/clicks (fade-in first 10%, fade-out last 20%)
                val volumeEnvelope = when {
                    t < 0.1f  -> t / 0.1f
                    t > 0.8f  -> (1f - t) / 0.2f
                    else      -> 1f
                }
                
                buffer[i] = (sin(angle) * 16384.0 * volumeEnvelope).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Release static track resources after playback completes
            Thread.sleep(durationMs.toLong() + 50L)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.liquidcolorsort.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.liquidcolorsort.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Real-time synthesized background music player for Liquid Color Sort.
 * Generates a soft, pleasant, non-obtrusive procedurally generated ambient arpeggio melody.
 * Respects system audio focus, music settings flow, and app foreground/background lifecycle.
 */
@Singleton
class BackgroundMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Default)
    private var musicJob: Job? = null
    private var isPlaying = false
    private var isAppInForeground = false

    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                resume()
            }
        }
    }

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        }

        // Keep track of settings updates
        scope.launch {
            settingsRepo.musicEnabled.collectLatest { enabled ->
                if (enabled) {
                    if (isAppInForeground) startPlayingLoop()
                } else {
                    stopPlayingLoop()
                }
            }
        }
    }

    /** Call in Activity onResume to restore background music. */
    fun onActivityResume() {
        isAppInForeground = true
        scope.launch {
            settingsRepo.musicEnabled.collectLatest { enabled ->
                if (enabled) startPlayingLoop()
            }
        }
    }

    /** Call in Activity onPause to stop background music automatically. */
    fun onActivityPause() {
        isAppInForeground = false
        stopPlayingLoop()
    }

    private fun startPlayingLoop() {
        if (isPlaying) return
        isPlaying = true

        val hasFocus = requestAudioFocus()
        if (!hasFocus) return

        musicJob = scope.launch {
            try {
                val sampleRate = 22050
                val bufferSize = (0.25f * sampleRate).toInt() // 250ms notes
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                      )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                // Simple soft chord progression (C -> Am -> F -> G)
                val notes = listOf(
                    // C4, E4, G4, C5
                    261.63f, 329.63f, 392.00f, 523.25f,
                    // A3, C4, E4, A4
                    220.00f, 261.63f, 329.63f, 440.00f,
                    // F3, A3, C4, F4
                    174.61f, 220.00f, 261.63f, 349.23f,
                    // G3, B3, D4, G4
                    196.00f, 246.94f, 293.66f, 392.00f
                )

                var noteIdx = 0
                val buffer = ShortArray(bufferSize)

                while (isPlaying) {
                    val freq = notes[noteIdx]
                    noteIdx = (noteIdx + 1) % notes.size

                    for (i in 0 until bufferSize) {
                        val t = i.toFloat() / bufferSize
                        val angle = 2.0 * Math.PI * i.toDouble() * freq / sampleRate
                        
                        // Soft amplitude (250) + smooth envelope to prevent popping/clicking
                        val env = when {
                            t < 0.1f -> t / 0.1f
                            t > 0.8f -> (1f - t) / 0.2f
                            else -> 1f
                        }
                        buffer[i] = (sin(angle) * 250.0 * env).toInt().toShort()
                    }

                    audioTrack.write(buffer, 0, buffer.size)
                    delay(240) // soft offset to keep PCM buffers streaming cleanly
                }

                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopPlayingLoop() {
        isPlaying = false
        musicJob?.cancel()
        musicJob = null
        abandonAudioFocus()
    }

    private fun pause() {
        isPlaying = false
        musicJob?.cancel()
        musicJob = null
    }

    private fun resume() {
        scope.launch {
            settingsRepo.musicEnabled.collectLatest { enabled ->
                if (enabled && isAppInForeground) {
                    startPlayingLoop()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = focusRequest ?: return false
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }
}

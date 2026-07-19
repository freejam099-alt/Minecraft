package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class SoundEffects {
    private val sampleRate = 11025
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun playTone(
        frequencies: FloatArray,
        durationsMs: IntArray,
        type: String = "sine"
    ) {
        scope.launch {
            try {
                var totalSamples = 0
                for (d in durationsMs) {
                    totalSamples += (sampleRate * d / 1000)
                }
                val buffer = ShortArray(totalSamples)
                var currentSample = 0

                val random = java.util.Random()

                for (i in frequencies.indices) {
                    val freq = frequencies[i]
                    val durationMs = durationsMs[i]
                    val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
                    
                    for (step in 0 until numSamples) {
                        if (currentSample >= buffer.size) break
                        
                        val t = step.toDouble() / sampleRate
                        var value = 0.0
                        
                        when (type) {
                            "sine" -> value = sin(2.0 * Math.PI * freq * t)
                            "square" -> value = if (sin(2.0 * Math.PI * freq * t) >= 0) 0.4 else -0.4
                            "noise" -> value = (random.nextFloat() * 2.0f - 1.0f).toDouble()
                        }
                        
                        // Apply quick attack and decay envelope
                        val envelope = if (step < numSamples * 0.1) {
                            step.toDouble() / (numSamples * 0.1) // Quick attack
                        } else {
                            1.0 - ((step - numSamples * 0.1) / (numSamples * 0.9)) // Gradual decay
                        }
                        
                        buffer[currentSample] = (value * 32767.0 * 0.25 * envelope).toInt().toShort()
                        currentSample++
                    }
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
                
                // Release native resource after playing
                val totalDuration = durationsMs.sum().toLong()
                scope.launch {
                    kotlinx.coroutines.delay(totalDuration + 200L)
                    audioTrack.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playJump() {
        playTone(floatArrayOf(150f, 300f), intArrayOf(40, 80), "sine")
    }

    fun playMine() {
        playTone(floatArrayOf(100f, 50f), intArrayOf(50, 50), "noise")
    }

    fun playPlace() {
        playTone(floatArrayOf(130f, 100f), intArrayOf(40, 40), "square")
    }

    fun playPlayerDamage() {
        playTone(floatArrayOf(150f, 100f), intArrayOf(80, 150), "sine")
    }

    fun playZombieDamage() {
        playTone(floatArrayOf(90f, 60f), intArrayOf(80, 120), "square")
    }

    fun playCraft() {
        playTone(floatArrayOf(261.63f, 329.63f, 392.00f), intArrayOf(50, 50, 120), "sine") // C E G arpeggio
    }

    fun playClick() {
        playTone(floatArrayOf(600f), intArrayOf(20), "sine")
    }
}

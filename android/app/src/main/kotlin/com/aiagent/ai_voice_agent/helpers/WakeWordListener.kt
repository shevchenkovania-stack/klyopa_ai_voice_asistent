package com.aiagent.ai_voice_agent.helpers

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.abs

/**
 * Wake word listener — AudioRecord (mic always open) + VAD + SpeechRecognizer.
 *
 * HOW IT ELIMINATES BUBBLING:
 * - AudioRecord keeps microphone open CONTINUOUSLY (no start/stop cycles)
 * - Simple energy-based VAD detects when user starts speaking
 * - SpeechRecognizer is ONLY started when voice activity is detected
 * - This means SpeechRecognizer cycles are rare (only on actual speech)
 * - No mic open/close cycling = no bubbling artifacts
 */
class WakeWordListener(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    @Volatile
    private var isListening = false
    private var isSpeechRecognizerActive = false
    private var wakeWord: String = ""
    private var wakeWordTriggered = false
    
    // VAD state
    private var speechDetected = false
    private var silenceFrames = 0
    private var speechFrames = 0
    
    // VAD thresholds
    private val speechEnergyThreshold = 500  // RMS threshold for voice
    private val silenceFrameThreshold = 30   // ~1.5s of silence to stop SR
    private val speechFrameThreshold = 5     // ~0.25s of voice to start SR
    
    var onPartialResult: ((String) -> Unit)? = null
    var onWakeWordMatched: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    fun setWakeWord(word: String) {
        wakeWord = word.lowercase()
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        wakeWordTriggered = false
        
        Thread {
            var localRecord: AudioRecord? = null
            try {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                localRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord = localRecord
                
                val buffer = ShortArray(bufferSize / 2)
                localRecord.startRecording()
                android.util.Log.d("WakeWord", "[AUDIORECORD] Mic open, listening...")
                
                while (isListening) {
                    val read = localRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        processFrame(buffer, read)
                    } else if (read < 0) {
                        // Error or stopped
                        break
                    }
                }
                
                android.util.Log.d("WakeWord", "[AUDIORECORD] Loop exited")
            } catch (e: Exception) {
                android.util.Log.e("WakeWord", "[AUDIORECORD] Error: ${e.message}")
                onError?.invoke("Ошибка микрофона: ${e.message}")
            } finally {
                // Background thread cleans up
                try {
                    localRecord?.stop()
                    localRecord?.release()
                    android.util.Log.d("WakeWord", "[AUDIORECORD] Released by background thread")
                } catch (e: Exception) {
                    android.util.Log.d("WakeWord", "[AUDIORECORD] Already released: ${e.message}")
                }
                audioRecord = null
            }
        }.start()
    }

    /**
     * Process audio frame — simple energy-based VAD
     * When speech detected → start SpeechRecognizer
     * When silence → stop SpeechRecognizer (but AudioRecord keeps running)
     */
    private fun processFrame(buffer: ShortArray, read: Int) {
        // Calculate RMS energy
        var sum = 0.0
        for (i in 0 until read) {
            sum += abs(buffer[i].toInt())
        }
        val rms = sum / read
        
        if (rms > speechEnergyThreshold) {
            // Voice detected
            speechFrames++
            silenceFrames = 0
            
            if (!speechDetected && speechFrames >= speechFrameThreshold) {
                speechDetected = true
                android.util.Log.d("WakeWord", "[VAD] Speech detected (RMS=${rms.toInt()}), starting SpeechRecognizer")
                startSpeechRecognizer()
            }
        } else {
            // Silence
            if (speechDetected) {
                silenceFrames++
                if (silenceFrames >= silenceFrameThreshold) {
                    speechDetected = false
                    speechFrames = 0
                    silenceFrames = 0
                    android.util.Log.d("WakeWord", "[VAD] Silence detected, stopping SpeechRecognizer")
                    stopSpeechRecognizer()
                }
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun startSpeechRecognizer() {
        if (isSpeechRecognizerActive) return
        
        // Must run on main thread - SpeechRecognizer requirement
        mainHandler.post {
            try {
                // Destroy old recognizer if any
                speechRecognizer?.destroy()
                speechRecognizer = null
                
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createListener())
                speechRecognizer?.startListening(createIntent())
                isSpeechRecognizerActive = true
                android.util.Log.d("WakeWord", "[SR] SpeechRecognizer started")
            } catch (e: Exception) {
                android.util.Log.e("WakeWord", "[SR] Failed to start: ${e.message}")
            }
        }
    }

    private fun stopSpeechRecognizer() {
        if (!isSpeechRecognizerActive) return
        // Must run on main thread - SpeechRecognizer requirement
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                isSpeechRecognizerActive = false
                android.util.Log.d("WakeWord", "[SR] SpeechRecognizer stopped")
            } catch (e: Exception) {
                android.util.Log.w("WakeWord", "[SR] Error stopping: ${e.message}")
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            android.util.Log.d("WakeWord", "[SR] Ready for speech")
        }
        
        override fun onBeginningOfSpeech() {
            android.util.Log.d("WakeWord", "[SR] User speaking")
        }
        
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            android.util.Log.d("WakeWord", "[SR] End of speech")
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (wakeWordTriggered) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                android.util.Log.d("WakeWord", "[PARTIAL] \"$text\"")
                onPartialResult?.invoke(text)
                
                // Check wake word
                if (wakeWord.isNotEmpty() && text.lowercase().contains(wakeWord)) {
                    wakeWordTriggered = true
                    android.util.Log.d("WakeWord", "[WAKE!] Wake word matched: \"$text\"")
                    onWakeWordMatched?.invoke()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (wakeWordTriggered) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                android.util.Log.d("WakeWord", "[RESULTS] \"$text\"")
                onPartialResult?.invoke(text)
                
                if (wakeWord.isNotEmpty() && text.lowercase().contains(wakeWord)) {
                    wakeWordTriggered = true
                    android.util.Log.d("WakeWord", "[WAKE!] Wake word matched: \"$text\"")
                    onWakeWordMatched?.invoke()
                }
            }
            // SR will be stopped by VAD silence detection
        }

        override fun onError(error: Int) {
            val errorName = when (error) {
                1 -> "NETWORK_TIMEOUT"
                2 -> "NETWORK"
                3 -> "AUDIO"
                4 -> "SERVER"
                5 -> "CLIENT"
                6 -> "SPEECH_TIMEOUT"
                7 -> "NO_MATCH"
                8 -> "RECOGNIZER_BUSY"
                9 -> "INSUFFICIENT_PERMISSIONS"
                10 -> "LANGUAGE_NOT_SUPPORTED"
                else -> "UNKNOWN($error)"
            }
            android.util.Log.d("WakeWord", "[SR ERROR] $errorName")
            isSpeechRecognizerActive = false
            // Will be restarted by VAD on next speech detection
        }
    }

    private fun createIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Long timeouts — SR stays open longer, fewer restarts
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
    }

    fun stopListening() {
        android.util.Log.d("WakeWord", "[STOP] Stopping...")
        isListening = false
        stopSpeechRecognizer()
        
        // Grab reference and null the field immediately
        val record = audioRecord
        audioRecord = null
        
        // Force stop the blocking read() — this makes read() return error
        try {
            record?.stop()
            android.util.Log.d("WakeWord", "[STOP] AudioRecord stopped")
        } catch (e: IllegalStateException) {
            android.util.Log.d("WakeWord", "[STOP] Already stopped: ${e.message}")
        }
        
        // Release — background thread's finally block will catch if already released
        try {
            record?.release()
            android.util.Log.d("WakeWord", "[STOP] AudioRecord released")
        } catch (e: Exception) {
            android.util.Log.d("WakeWord", "[STOP] Release error: ${e.message}")
        }
        
        handler.removeCallbacksAndMessages(null)
    }
}

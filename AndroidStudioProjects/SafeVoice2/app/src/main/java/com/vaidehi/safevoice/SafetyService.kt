package com.vaidehi.safevoice

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.*

class SafetyService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent
    private val channelId = "SafetyVoiceService"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, createNotification())
        setupVoiceTrigger()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Safety Voice Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeVoice is Active")
            .setContentText("Listening for help triggers...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun setupVoiceTrigger() {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
                
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach {
                            if (it.lowercase().contains("help") || it.lowercase().contains("emergency")) {
                                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra("trigger_sos", true)
                                }
                                startActivity(intent)
                            }
                        }
                        speechRecognizer?.startListening(speechIntent)
                    }
                    override fun onError(error: Int) { speechRecognizer?.startListening(speechIntent) }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(speechIntent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.pain.jarvis.services

import ai.picovoice.cheetah.Cheetah
import ai.picovoice.cheetah.CheetahException
import ai.picovoice.cheetah.CheetahTranscript
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import com.pain.jarvis.MainActivity2
import com.pain.jarvis.R
import java.util.Locale

class JarvisService : Service() {

    private lateinit var porcupineManager: PorcupineManager
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private var lastPartialSpokenText: String = ""


    private var cheetah: Cheetah? = null
    private lateinit var audioRecorder: AudioRecord
    private var isListening = false



    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        Log.d("Jarvis_log", "CREATED")
        startWakeWordDetection()
    }

    private fun startForegroundService() {
        val channelId = "jarvis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Channel", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis is listening")
            .setContentText("Say 'Hey Jarvis' to activate.")
            .setSmallIcon(R.drawable.baseline_ac_unit_24)
            .build()

        startForeground(1, notification)
    }

    private fun startWakeWordDetection() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setKeywordPath("hey_jarvis.ppn")
                .setAccessKey("mCU/r2dC14KiSybkrm9UUS2V9XK8KExK6cC00FECfCIHCakbq0d2Tw==")
                .build(this) {
                    Log.d("Jarvis_log", "Wake word detected!")
                    Toast.makeText(applicationContext, "Jarvis Activated!", Toast.LENGTH_SHORT).show()

                    try {
                        cheetah = Cheetah.Builder()
                            .setAccessKey("mCU/r2dC14KiSybkrm9UUS2V9XK8KExK6cC00FECfCIHCakbq0d2Tw==")
                            .setModelPath("cheetah_params.pv")
                            .build(this)
                    } catch (e: CheetahException) {
                        Log.e("Jarvis_log", "Cheetah init failed: ${e.message}")
                    }
                    startCheetahListening()
                }
            porcupineManager.start()
        } catch (e: Exception) {
            Log.e("Jarvis_log", "Wake word failed: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun startCheetahListening() {
        val sampleRate = cheetah!!.sampleRate
        val frameLength = cheetah!!.frameLength
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )

        if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("Jarvis_log", "AudioRecord failed to initialize!")
            return
        }

        isListening = true
        audioRecorder?.startRecording()

        Log.d("Jarvis_log", "🎙️ Cheetah started listening")

        Thread {
            val buffer = ShortArray(frameLength)
            val transcriptBuilder = StringBuilder()
            val startTime = System.currentTimeMillis()

            while (isListening) {
                val readCount = audioRecorder?.read(buffer, 0, frameLength) ?: 0
                if (readCount > 0) {
                    val result = cheetah!!.process(buffer)
                    transcriptBuilder.append(result.transcript)

                    if (result.transcript.isNotBlank()) {
                        Log.d("Jarvis_log", "Partial: ${result.transcript}")
                    }

                    if (result.isEndpoint || (System.currentTimeMillis() - startTime > 5000)) {
                        val finalResult = cheetah!!.flush()
                        transcriptBuilder.append(finalResult.transcript)

                        val full = transcriptBuilder.toString().trim()
                        Log.d("Jarvis_log", "✅ Final Transcript: $full")

                        handleCommand(full)
                        stopCheetahListening()
                        break
                    }
                }
            }
        }.start()
    }


    private fun stopCheetahListening() {
        isListening = false
        try {
            audioRecorder?.stop()
            audioRecorder?.release()
            audioRecorder
        } catch (_: Exception) {
        }
    }

    private fun handleCommand(text: String) {
        if (text.lowercase().contains("youtube", ignoreCase = true)) {
            openYouTube()
        }
        // Add more logic here
    }


    private fun openYouTube() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Log.d("Jarvis_log", "YouTube app not installed.")
        }
    }

    override fun onDestroy() {
        porcupineManager.stop()
        porcupineManager.delete()
        isListening = false
        audioRecorder.stop()
        audioRecorder.release()
        cheetah?.delete()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

}

package com.example.VoiceVigil.audioInference

import android.Manifest
import com.example.VoiceVigil.R

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class SoundClassifier(private val context: Context) : SensorEventListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    // detector listener
    private var detectorListener: RecognitionListener? = null

    private var mediaRecorder: MediaRecorder? = null
    private var notificationId = 1
    private var notificationManager: NotificationManagerCompat? = null
    private var stopRecordingPendingIntent: PendingIntent? = null

    // TimerTask
    private var task: TimerTask? = null
    private var isListening = false
    private var hasMoved = false

    fun setDetectorListener(listener: RecognitionListener) {
        detectorListener = listener
    }

    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            val completeSilenceLengthMillis = 2000
            // duration for which it will listen
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceLengthMillis)

            speechRecognizer.setRecognitionListener(detectorListener)

            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        else {
            exitProcess(-1)
        }
    }

    // trigger silent notification if the camera recording starts
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recording Channel"
            val descriptionText = "Channel for camera recording notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("recording_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun handleRecognitionError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NETWORK -> Log.e(TAG, "Network Error")
            SpeechRecognizer.ERROR_AUDIO -> Log.e(TAG, "Audio Error")
            else -> Log.e(TAG, "Other Error: $error")
        }
    }

    fun handleRecognitionResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.let {
            if (it.any { result -> result.contains("help") }) {
                // If any of the results contain the distress keyword, start the camera recording.
                startCameraRecording()
            }
        }
    }

    private fun showNotification() {
        val builder = NotificationCompat.Builder(context, "recording_channel")
            .setSmallIcon(R.drawable.notification)
            .setContentTitle("Camera Recording")
            .setContentText("Recording in progress")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.stop, "Stop recording", stopRecordingPendingIntent)
            .setSilent(true)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
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
        notificationManager?.notify(notificationId, builder.build())
    }

    private fun startCameraRecording() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)

        // Create a file to save the recorded video
        val videoFile = createVideoFile()
        val videoUri = FileProvider.getUriForFile(context, "com.example.VoiceVigil.fileprovider", videoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)

        // Check if the device has a camera
        if (intent.resolveActivity(context.packageManager) != null) {
            (context as Activity).startActivityForResult(intent, REQUEST_VIDEO_CAPTURE)

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.DEFAULT)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setOutputFile(videoFile.path)
                prepare()
                start()
            }
        } else {
            // Handle case where the device does not have a camera or camera app is not available
            Log.e(TAG, "No camera app available")
        }
    }

    private fun stopCameraRecording() {
        mediaRecorder?.apply {
            stop()
            reset()
            release()
        }
        mediaRecorder = null
    }


    private fun createVideoFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VIDEO_$timeStamp"

        // Get the directory for storing videos
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

        // Create the video file
        val videoFile = File.createTempFile(videoFileName, ".mp4", storageDir)

        // Add the video file to the media gallery
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val contentUri = Uri.fromFile(videoFile)
        mediaScanIntent.data = contentUri
        context.sendBroadcast(mediaScanIntent)  // send the broadcast

        return videoFile
    }


    private fun startListening() {
        if (!isListening) {
            speechRecognizer.startListening(recognizerIntent)
            Log.d(TAG, "Listening started!")
            isListening = true
        }
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer.cancel()
            Log.d(TAG, "Listening stopped.")
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate movement
            val movement = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // Start listening if movement is detected
            if (movement > MOVEMENT_THRESHOLD) {
                hasMoved = true
                startListening()
            } else if (hasMoved && movement <= MOVEMENT_THRESHOLD) {
                hasMoved = false
                stopListening()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "SoundClassifier"
//        private const val MOVEMENT_THRESHOLD = 0
        private const val MOVEMENT_THRESHOLD = 2.0f
        private const val REQUEST_VIDEO_CAPTURE = 1
    }

    class StopRecordingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val soundClassifier = SoundClassifier(context!!)
            soundClassifier.stopCameraRecording()
            soundClassifier.notificationManager?.cancel(soundClassifier.notificationId)
        }
    }
}


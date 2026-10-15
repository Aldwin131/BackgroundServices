package com.example.backgroundservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs

class TrackerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2
    private var isLatihanMode = false
    private var stepCount = 0
    private var lastSensorTime = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        isLatihanMode = intent?.action == "ACTION_START_LATIHAN"
        stepCount = 0

        Log.d("TrackerService", if (isLatihanMode) "Service Latihan onStartCommand" else "Service Biasa onStartCommand")

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID_SERVICE_RUNNING,
                    buildServiceRunningNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(
                    NOTIFICATION_ID_SERVICE_RUNNING,
                    buildServiceRunningNotification()
                )
            }
        } catch (e: Exception) {
            Log.e("TrackerService", "Gagal: ${e.message}")
        }

        startJumpDetection()
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING, "Service Running Channel", NotificationManager.IMPORTANCE_LOW)
            val jumpChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_JUMP_DETECTED, "Jump Detected Channel", NotificationManager.IMPORTANCE_HIGH)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)
        }
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (isLatihanMode) "Mode Latihan Berjalan" else "Pelacak Aktivitas Latar"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle(titleText)
            .setContentText("Aplikasi Tracker Services sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val currentTime = System.currentTimeMillis()

            if (isLatihanMode) {
                val walkThreshold = 10.5

                if (abs(y) > walkThreshold && (currentTime - lastSensorTime > 1000)) {
                    lastSensorTime = currentTime
                    stepCount++
                    Log.d("TrackerService", "Langkah ke-$stepCount terdeteksi!")

                    if (stepCount > 2) {
                        Log.d("TrackerService", "Latihan Selesai! Melebihi 2 langkah.")
                        sensorManager.unregisterListener(this)

                        stopSelf()
                    }
                }

            } else {
                val jumpThreshold = 15.0
                if (abs(y) > jumpThreshold) {
                    Log.d("TrackerService", "LOMPATAN Terdeteksi! y: $y")
                    sensorManager.unregisterListener(this)
                    showJumpNotification()
                    stopSelf()
                }
            }
        }
    }

    private fun showJumpNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_JUMP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle("Lompatan Terdeteksi")
            .setContentText("Service Berhenti. Ketuk untuk membuka aplikasi.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch (e: SecurityException) {
            Log.e("TrackerService", "Gagal: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("TrackerService", "Service onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
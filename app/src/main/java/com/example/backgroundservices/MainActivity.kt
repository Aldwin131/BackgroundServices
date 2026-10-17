package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnLatihan: Button

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)
        btnLatihan = findViewById(R.id.btnLatihan)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnStart.setOnClickListener {
            if (hasAllPermissions()) {
                startTrackerService()
            } else {
                ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_CODE_PERMISSIONS)
            }
        }

        btnLatihan.setOnClickListener {
            if (hasAllPermissions()) {
                val serviceIntent = Intent(this, TrackerService::class.java)
                serviceIntent.action = "ACTION_START_LATIHAN"
                ContextCompat.startForegroundService(this, serviceIntent)
                statusTextView.text = "Latihan Sedang Berjalan..."
            } else {
                ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_CODE_PERMISSIONS)
            }
        }

        handleIntent(intent)
    }

    private fun hasAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackerService() {
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Background service sedang berjalan..."
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SERVICE_STOPPED_JUMP) {
            statusTextView.text = "Background services sudah berhenti karena Anda melompat"
        }

        else if (intent?.action == ACTION_LATIHAN_SELESAI) {
            val totalLangkah = intent.getIntExtra("STEP_COUNT", 0)

            statusTextView.text = "Latihan Selesai! Anda mencapai $totalLangkah langkah/lompatan."

            // Buka aplikasi kalender
            bukaKalender()
        }
    }

    private fun bukaKalender() {
        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Latihan Fisik Selesai")
            putExtra(CalendarContract.Events.DESCRIPTION, "Berhasil menyelesaikan latihan harian dari aplikasi Background Services.")
        }

        try {
            startActivity(calendarIntent)
        } catch (e: Exception) {
            statusTextView.text = "Latihan Selesai! (Aplikasi Kalender tidak ditemukan di perangkat ini)"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    companion object{
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_JUMP"
        const val ACTION_LATIHAN_SELESAI = "com.example.backgroundservices.ACTION_LATIHAN_SELESAI" // Konstanta baru
        const val REQUEST_CODE_PERMISSIONS = 101
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                val serviceIntent = Intent(this, TrackerService::class.java)
                ContextCompat.startForegroundService(this,serviceIntent)
                finish()
            } else {
                statusTextView.text = "Izin ditolak, service tidak dapat dijalankan."
            }
        }
    }
}
package com.ejemplo.sistemamonitoreo.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ejemplo.sistemamonitoreo.R
import com.ejemplo.sistemamonitoreo.data.local.db.AppDatabase
import com.ejemplo.sistemamonitoreo.data.local.db.entities.GpsData
// --- CORRECCIÓN AQUÍ ---
// Se cambió el import para que apunte a WebServer en lugar de al inexistente ApiServer.
import com.ejemplo.sistemamonitoreo.services.server.WebServer
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: AppDatabase
    // --- CORRECCIÓN AQUÍ ---
    private lateinit var webServer: WebServer // Se cambió el nombre de la variable

    private lateinit var deviceId: String

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getDatabase(this)
        // --- CORRECCIÓN AQUÍ ---
        webServer = WebServer(this, db) // Se instancia la clase correcta
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        setupLocationCallback()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                startForegroundService()
                startLocationUpdates()
                serviceScope.launch {
                    // --- CORRECCIÓN AQUÍ ---
                    webServer.start() // Se llama al método en la instancia correcta
                }
            }
            ACTION_STOP -> {
                stopService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "monitoring_channel"
        val channelName = "Servicio de Monitoreo"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sistema de Monitoreo Activo")
            .setContentText("Servidor API corriendo en el puerto 8080.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(15000)
            .setMaxUpdateDelayMillis(60000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val gpsData = GpsData(
                        deviceId = deviceId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = getIso8601Timestamp()
                    )
                    serviceScope.launch {
                        db.gpsDataDao().insertGpsData(gpsData)
                    }
                }
            }
        }
    }

    private fun stopService() {
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.launch {
            // --- CORRECCIÓN AQUÍ ---
            webServer.stop() // Se llama al método en la instancia correcta
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getIso8601Timestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // --- CORRECCIÓN AQUÍ ---
        webServer.stop() // Se llama al método en la instancia correcta
    }

    companion object {
        var isRunning = false
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 1
    }
}

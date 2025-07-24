package com.ejemplo.sistemamonitoreo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ejemplo.sistemamonitoreo.data.local.db.AppDatabase
import com.ejemplo.sistemamonitoreo.data.local.db.entities.Credential
import com.ejemplo.sistemamonitoreo.databinding.ActivityMainBinding
import com.ejemplo.sistemamonitoreo.services.MonitoringService
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        setupEventListeners()
    }

    override fun onResume() {
        super.onResume()
        // Actualiza la UI cada vez que el usuario vuelve a la app.
        updateUI()
    }

    private fun setupEventListeners() {
        binding.btnStartService.setOnClickListener {
            checkPermissionsAndStartService()
        }
        binding.btnStopService.setOnClickListener {
            stopMonitoringService()
        }
        binding.btnGenerateToken.setOnClickListener {
            generateAndShowToken()
        }
    }

    private fun updateUI() {
        if (isServiceRunning()) {
            binding.tvServiceStatus.text = "Activo"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_color_running))
            binding.tvServiceStatus.setBackgroundResource(R.drawable.status_background_running)
            binding.btnStartService.isEnabled = false
            binding.btnStopService.isEnabled = true
        } else {
            binding.tvServiceStatus.text = "Detenido"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_color_stopped))
            binding.tvServiceStatus.setBackgroundResource(R.drawable.status_background_stopped)
            binding.btnStartService.isEnabled = true
            binding.btnStopService.isEnabled = false
        }
        displayIpAddress()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Servicio de monitoreo iniciado.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar el servicio: ${e.message}", Toast.LENGTH_LONG).show()
        }
        // Llamar a updateUI después de un breve retraso para dar tiempo al servicio a actualizar su estado
        binding.root.postDelayed({ updateUI() }, 500)
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Servicio de monitoreo detenido.", Toast.LENGTH_SHORT).show()
        binding.root.postDelayed({ updateUI() }, 500)
    }

    private fun generateAndShowToken() {
        lifecycleScope.launch {
            val existingToken = db.credentialDao().getCredentialByKey("api_token")
            if (existingToken != null) {
                binding.tvApiToken.text = "Token: ${existingToken.value}"
                Toast.makeText(this@MainActivity, "Token existente mostrado.", Toast.LENGTH_SHORT).show()
            } else {
                val newToken = "token_${UUID.randomUUID()}"
                db.credentialDao().insertCredential(Credential("api_token", newToken))
                binding.tvApiToken.text = "Token: $newToken"
                Toast.makeText(this@MainActivity, "Nuevo token generado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayIpAddress() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress == 0) {
                binding.tvIpAddress.text = "Dirección IP: No conectado a una red WiFi"
            } else {
                val formattedIpAddress = String.format(
                    "%d.%d.%d.%d",
                    (ipAddress and 0xff),
                    (ipAddress shr 8 and 0xff),
                    (ipAddress shr 16 and 0xff),
                    (ipAddress shr 24 and 0xff)
                )
                binding.tvIpAddress.text = "Dirección IP: $formattedIpAddress:8080"
            }
        } catch (e: Exception) {
            binding.tvIpAddress.text = "Dirección IP: Error al obtener"
        }
    }

    private fun isServiceRunning(): Boolean {
        return MonitoringService.isRunning
    }

    // --- Gestión de Permisos ---
    private fun checkPermissionsAndStartService() {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            // Si los permisos básicos están concedidos, se verifica el de segundo plano.
            checkBackgroundLocationPermission()
        } else {
            // Si falta algún permiso básico, se solicitan.
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                // Si se conceden los permisos básicos, se pasa a verificar el de segundo plano.
                checkBackgroundLocationPermission()
            } else {
                Toast.makeText(this, "Se requieren permisos de ubicación y/o notificación.", Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }

    private fun checkBackgroundLocationPermission() {
        // Esta función solo se llama si los permisos básicos ya están concedidos.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Verificando permiso de segundo plano...", Toast.LENGTH_SHORT).show()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de segundo plano: CONCEDIDO. Iniciando servicio.", Toast.LENGTH_LONG).show()
                startMonitoringService()
            } else {
                // Si el permiso de segundo plano no está concedido, se solicita.
                Toast.makeText(this, "Permiso de segundo plano: DENEGADO. Solicitando...", Toast.LENGTH_LONG).show()
                requestBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            // En versiones anteriores a Android Q, no se necesita este permiso.
            startMonitoringService()
        }
    }

    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startMonitoringService()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permiso de Ubicación en Segundo Plano Requerido")
                    .setMessage("Para que la recolección de datos funcione cuando la app no está abierta, es necesario conceder el permiso 'Permitir todo el tiempo'.\n\nPor favor, ve a 'Ajustes' -> 'Permisos' -> 'Ubicación' y selecciona 'Permitir todo el tiempo'.")
                    .setPositiveButton("Ir a Ajustes") { _, _ -> openAppSettings() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).also {
            val uri = Uri.fromParts("package", packageName, null)
            it.data = uri
            startActivity(it)
        }
    }
}

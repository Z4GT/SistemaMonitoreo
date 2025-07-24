package com.ejemplo.sistemamonitoreo.services.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.ejemplo.sistemamonitoreo.data.local.db.AppDatabase
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class WebServer(private val context: Context, private val db: AppDatabase) {

    private var server: NettyApplicationEngine? = null

    fun start() {
        // El servidor se ejecuta en el hilo de Dispatchers.IO para no bloquear el hilo principal
        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            // --- PLUGINS ---
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
            install(Authentication) {
                // Configuración de autenticación Bearer
                bearer("auth-bearer") {
                    authenticate { tokenCredential ->
                        val storedToken = withContext(Dispatchers.IO) {
                            db.credentialDao().getCredentialByKey("api_token")?.value
                        }
                        if (storedToken != null && tokenCredential.token == storedToken) {
                            UserIdPrincipal(tokenCredential.token) // Autenticación exitosa
                        } else {
                            null // Falla de autenticación
                        }
                    }
                }
            }

            // --- RUTAS DE LA API ---
            routing {
                authenticate("auth-bearer") {
                    // Endpoint para obtener el estado del dispositivo
                    get("/api/device_status") {
                        val status = getDeviceStatus()
                        call.respond(status)
                    }

                    // Endpoint para obtener datos del sensor por rango de fechas
                    get("/api/sensor_data") {
                        val startTime = call.request.queryParameters["start_time"]
                        val endTime = call.request.queryParameters["end_time"]

                        if (startTime == null || endTime == null) {
                            call.respond(mapOf("error" to "Los parámetros 'start_time' y 'end_time' son requeridos."))
                            return@get
                        }

                        // Validar formato de fecha (simplificado)
                        if (!isValidIso8601(startTime) || !isValidIso8601(endTime)) {
                            call.respond(mapOf("error" to "El formato de fecha debe ser ISO 8601 (ej. YYYY-MM-DD'T'HH:mm:ss'Z')."))
                            return@get
                        }

                        val data = db.gpsDataDao().getGpsDataByDateRange(startTime, endTime)
                        call.respond(data)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun getDeviceStatus(): DeviceStatus {
        // Batería
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Conectividad
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        val isConnected = activeNetwork?.isConnectedOrConnecting == true
        val networkType = activeNetwork?.typeName ?: "N/A"

        // Almacenamiento
        val statFs = StatFs(Environment.getDataDirectory().path)
        val availableBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes
        val availableStorage = "${availableBytes / (1024 * 1024)} MB"
        val totalStorage = "${totalBytes / (1024 * 1024)} MB"

        return DeviceStatus(
            batteryLevel = "$batteryPct%",
            networkStatus = if (isConnected) "Conectado ($networkType)" else "Desconectado",
            availableStorage = availableStorage,
            totalStorage = totalStorage,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun getIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress == 0) return "No disponible (conéctate a una red WiFi)"
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    private fun isValidIso8601(date: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(date)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Clase de datos para la respuesta de /api/device_status
@Serializable
data class DeviceStatus(
    val batteryLevel: String,
    val networkStatus: String,
    val availableStorage: String,
    val totalStorage: String,
    val osVersion: String,
    val deviceModel: String
)

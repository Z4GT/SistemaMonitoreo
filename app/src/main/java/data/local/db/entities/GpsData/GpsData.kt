package com.ejemplo.sistemamonitoreo.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Define la tabla 'gps_data' en la base de datos.
 * Cada instancia de esta clase representa una fila en la tabla.
 *
 * @property id Identificador único autoincremental para cada registro.
 * @property deviceId Identificador único del dispositivo que recolecta el dato.
 * @property latitude La latitud del punto GPS.
 * @property longitude La longitud del punto GPS.
 * @property timestamp La fecha y hora en formato ISO 8601 de cuándo se tomó el dato.
 */
@Serializable // Permite que Ktor convierta esta clase a JSON automáticamente
@Entity(tableName = "gps_data")
data class GpsData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

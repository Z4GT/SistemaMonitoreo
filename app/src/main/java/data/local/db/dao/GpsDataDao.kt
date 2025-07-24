package com.ejemplo.sistemamonitoreo.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ejemplo.sistemamonitoreo.data.local.db.entities.GpsData

@Dao
interface GpsDataDao {

    /**
     * Inserta un nuevo registro de datos GPS en la base de datos.
     * Si ya existe, no hace nada.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGpsData(gpsData: GpsData)

    /**
     * Obtiene todos los registros de GPS dentro de un rango de tiempo específico.
     * Las fechas deben estar en formato ISO 8601 (ej. "2023-10-27T10:00:00Z").
     *
     * @param startTime La fecha/hora de inicio del rango.
     * @param endTime La fecha/hora de fin del rango.
     * @return Una lista de objetos GpsData.
     */
    @Query("SELECT * FROM gps_data WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getGpsDataByDateRange(startTime: String, endTime: String): List<GpsData>
}

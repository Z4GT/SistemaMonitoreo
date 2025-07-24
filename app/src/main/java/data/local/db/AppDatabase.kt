package com.ejemplo.sistemamonitoreo.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ejemplo.sistemamonitoreo.data.local.db.dao.CredentialDao
import com.ejemplo.sistemamonitoreo.data.local.db.dao.GpsDataDao
import com.ejemplo.sistemamonitoreo.data.local.db.entities.Credential
import com.ejemplo.sistemamonitoreo.data.local.db.entities.GpsData

@Database(entities = [GpsData::class, Credential::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gpsDataDao(): GpsDataDao
    abstract fun credentialDao(): CredentialDao

    companion object {
        // La anotación @Volatile asegura que el valor de INSTANCE sea siempre actualizado
        // y visible para todos los hilos de ejecución.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Si la instancia no es nula, la retorna.
            // Si es nula, crea la base de datos en un bloque synchronized para evitar
            // que múltiples hilos la creen al mismo tiempo.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monitoring_database"
                )
                    .fallbackToDestructiveMigration() // Permite a Room recrear las tablas si se actualiza la versión
                    .build()
                INSTANCE = instance
                // retorna la instancia
                instance
            }
        }
    }
}

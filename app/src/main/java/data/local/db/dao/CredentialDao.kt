package com.ejemplo.sistemamonitoreo.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ejemplo.sistemamonitoreo.data.local.db.entities.Credential

@Dao
interface CredentialDao {

    /**
     * Inserta o reemplaza una credencial. Útil para guardar o actualizar el token.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential)

    /**
     * Obtiene una credencial por su clave.
     *
     * @param key La clave de la credencial a buscar (ej. "api_token").
     * @return El objeto Credential o null si no se encuentra.
     */
    @Query("SELECT * FROM credentials WHERE `key` = :key LIMIT 1")
    suspend fun getCredentialByKey(key: String): Credential?
}

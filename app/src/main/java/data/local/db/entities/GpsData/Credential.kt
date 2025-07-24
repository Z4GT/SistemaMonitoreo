package com.ejemplo.sistemamonitoreo.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Define la tabla 'credentials' para almacenar pares clave-valor, como el token de API.
 *
 * @property key La clave única para la credencial (ej. "api_token").
 * @property value El valor asociado a la clave (ej. el token).
 */
@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey
    val key: String,
    val value: String
)

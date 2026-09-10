package br.com.betinhos.atalocal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_installs")
data class ModelInstallEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val version: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val installedAtEpochMs: Long = System.currentTimeMillis()
)

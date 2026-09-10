package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelInstallDao {
    @Query("SELECT * FROM model_installs ORDER BY kind")
    fun observeAll(): Flow<List<ModelInstallEntity>>

    @Upsert
    suspend fun upsert(model: ModelInstallEntity)

    @Query("DELETE FROM model_installs WHERE id = :id")
    suspend fun delete(id: String)
}

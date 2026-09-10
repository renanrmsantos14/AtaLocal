package br.com.betinhos.atalocal.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile private var instance: AtaLocalDatabase? = null

    fun get(context: Context): AtaLocalDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            AtaLocalDatabase::class.java,
            "atalocal.db"
        ).addMigrations(MIGRATION_1_2).build().also { instance = it }
    }
}

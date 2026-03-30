package com.vaidehi.safevoice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Incident::class], version = 1, exportSchema = false)
abstract class SafeVoiceDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile
        private var INSTANCE: SafeVoiceDatabase? = null

        fun getDatabase(context: Context): SafeVoiceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeVoiceDatabase::class.java,
                    "safevoice_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

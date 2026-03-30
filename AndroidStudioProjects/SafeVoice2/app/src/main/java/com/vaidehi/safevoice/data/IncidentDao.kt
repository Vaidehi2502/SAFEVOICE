package com.vaidehi.safevoice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<Incident>>

    @Insert
    suspend fun insertIncident(incident: Incident)

    @Query("DELETE FROM incidents")
    suspend fun deleteAll()
}

package com.vaidehi.safevoice

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaidehi.safevoice.data.Incident
import com.vaidehi.safevoice.data.SafeVoiceDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SafeVoiceDatabase.getDatabase(application)
    private val incidentDao = database.incidentDao()

    val allIncidents: Flow<List<Incident>> = incidentDao.getAllIncidents()

    var isTrackingTrip by mutableStateOf(false)
        private set

    fun toggleTripTracking() {
        isTrackingTrip = !isTrackingTrip
        // Logic for background tracking will be handled by a Service
    }

    fun reportIncident(type: String, latitude: Double, longitude: Double, description: String = "") {
        viewModelScope.launch {
            incidentDao.insertIncident(
                Incident(type = type, latitude = latitude, longitude = longitude, description = description)
            )
        }
    }
}

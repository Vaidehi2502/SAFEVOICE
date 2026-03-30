package com.vaidehi.safevoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var emergencyContacts = mutableListOf<EmergencyContact>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        loadContacts()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val mapLink = "https://www.google.com/maps?q=${location.latitude},${location.longitude}"
                val message = "SafeVoice FollowMe Update: My current location: $mapLink"
                
                emergencyContacts.forEach { contact ->
                    sendDirectSms(contact.number, message)
                }
            }
        }
    }

    private fun loadContacts() {
        val sharedPref = getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val contactsJson = sharedPref.getString("emergency_contacts", null)
        if (contactsJson != null) {
            try {
                val jsonArray = JSONArray(contactsJson)
                emergencyContacts.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    emergencyContacts.add(EmergencyContact(
                        jsonObject.getString("name"),
                        jsonObject.getString("number")
                    ))
                }
            } catch (e: Exception) {}
        }
    }

    private fun sendDirectSms(phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "SafeVoiceTracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Trip Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeVoice Trip Tracking Active")
            .setContentText("Sending your location to emergency contacts...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300000) // 5 minutes
            .setMinUpdateIntervalMillis(60000) // 1 minute
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.vaidehi.safevoice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import com.vaidehi.safevoice.ui.theme.SafeVoiceTheme
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.net.URLEncoder

// Core Data Model
data class EmergencyContact(val name: String, val number: String)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var emergencyContacts = mutableStateListOf<EmergencyContact>()
    private var isDarkMode by mutableStateOf(false)

    private var isSosCallingActive by mutableStateOf(false)
    private var currentCallingIndex = 0
    private lateinit var telephonyManager: TelephonyManager

    private var sosCountdown by mutableIntStateOf(0)
    private val countdownHandler = Handler(Looper.getMainLooper())

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    // Updated Call Listener for Android 12+
    private val telephonyCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }
    } else null

    private fun handleCallStateChange(state: Int) {
        if (state == TelephonyManager.CALL_STATE_IDLE && isSosCallingActive) {
            currentCallingIndex++
            if (currentCallingIndex < emergencyContacts.size) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isSosCallingActive) makeEmergencyCall(emergencyContacts[currentCallingIndex].number)
                }, 2000)
            } else {
                stopSosCalling()
            }
        }
    }

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val rawNumber = cursor.getString(numberIndex)
                        val name = cursor.getString(nameIndex)
                        val cleanNumber = formatPhoneNumber(rawNumber)

                        if (!emergencyContacts.any { it.number == cleanNumber }) {
                            emergencyContacts.add(EmergencyContact(name, cleanNumber))
                            saveContacts()
                        }
                    }
                }
            }
        }
    }

    private fun formatPhoneNumber(number: String): String {
        val digits = number.replace(Regex("[^0-9]"), "")
        return if (digits.length == 10) "+91$digits"
        else if (digits.startsWith("91") && digits.length == 12) "+$digits"
        else if (number.startsWith("+")) number
        else "+91$digits"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMdroid Configuration
        Configuration.getInstance().load(this, getPreferences(Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        loadSettings()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        setContent {
            val viewModel: MainViewModel = viewModel()
            SafeVoiceTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var currentScreen by remember { mutableStateOf("home") }

                    when (currentScreen) {
                        "home" -> HomeScreen(
                            emergencyContacts = emergencyContacts,
                            isDarkMode = isDarkMode,
                            isSosActive = isSosCallingActive,
                            sosCountdown = sosCountdown,
                            isTrackingTrip = viewModel.isTrackingTrip,
                            onToggleDarkMode = { isDarkMode = !isDarkMode; saveSettings() },
                            onAddContact = { checkContactPermission() },
                            onRemoveContact = { emergencyContacts.remove(it); saveContacts() },
                            onSosClick = { startSosWithCountdown() },
                            onStopSos = { cancelSos() },
                            onNavigate = { currentScreen = it },
                            onToggleTrip = { viewModel.toggleTripTracking() },
                            onOpenBatterySettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        )
                        "heatmap" -> MapScreen(viewModel, fusedLocationClient) { currentScreen = "home" }
                        "report" -> ReportScreen(viewModel, fusedLocationClient) { currentScreen = "home" }
                        "settings" -> SettingsScreen(onBack = { currentScreen = "home" }, onOpenBattery = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
                    }
                }
            }
        }
    }

    private fun startSosWithCountdown() {
        if (emergencyContacts.isEmpty()) {
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_SHORT).show()
            return
        }
        sosCountdown = 5
        countdownHandler.post(object : Runnable {
            override fun run() {
                if (sosCountdown > 0) {
                    sosCountdown--
                    countdownHandler.postDelayed(this, 1000)
                } else if (sosCountdown == 0) {
                    triggerFullEmergency()
                }
            }
        })
    }

    private fun cancelSos() {
        sosCountdown = 0
        countdownHandler.removeCallbacksAndMessages(null)
        stopSosCalling()
        stopEvidenceCollection()
    }

    private fun triggerFullEmergency() {
        sendSmsAndWhatsAppToAll()
        startEvidenceCollection()
        if (emergencyContacts.isNotEmpty()) {
            isSosCallingActive = true
            currentCallingIndex = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
            }
            makeEmergencyCall(emergencyContacts[currentCallingIndex].number)
        }
    }

    private fun stopSosCalling() {
        isSosCallingActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        }
    }

    private fun startEvidenceCollection() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                val file = File(getExternalFilesDir(null), "evidence_${System.currentTimeMillis()}.mp3")
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
            } catch (e: Exception) {
                Log.e("SafeVoice", "MediaRecorder failed: ${e.message}")
            }
        }
    }

    private fun stopEvidenceCollection() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) { Log.e("SafeVoice", "Stop failed", e) }
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun sendSmsAndWhatsAppToAll() {
        val contacts = emergencyContacts.toList()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            // If location permission is missing, send alerts without coordinates
            val msg = "SAFEVOICE ALERT: I am in danger! (Location information unavailable)"
            contacts.forEach { contact -> sendDirectSms(contact.number, msg) }
            if (contacts.isNotEmpty()) sendWhatsAppDirect(contacts[0].number, msg)
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: android.location.Location? ->
                location?.let {
                    val mapLink = "https://www.google.com/maps?q=${it.latitude},${it.longitude}"
                    val msg = "SAFEVOICE ALERT: I am in danger! My location: $mapLink"

                    contacts.forEach { contact ->
                        sendDirectSms(contact.number, msg)
                    }
                    if (contacts.isNotEmpty()) {
                        sendWhatsAppDirect(contacts[0].number, msg)
                    }
                } ?: run {
                    // Location was null, send fallback message
                    val msg = "SAFEVOICE ALERT: I am in danger!"
                    contacts.forEach { contact -> sendDirectSms(contact.number, msg) }
                    if (contacts.isNotEmpty()) sendWhatsAppDirect(contacts[0].number, msg)
                }
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
        } catch (e: Exception) {
            Log.e("SafeVoice", "SMS sending failed: ${e.message}")
        }
    }

    private fun sendWhatsAppDirect(phoneNumber: String, message: String) {
        try {
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("SafeVoice", "WhatsApp failed", e)
        }
    }

    private fun makeEmergencyCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        }
    }

    private fun checkContactPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 102)
        }
    }

    private fun saveContacts() {
        val sharedPref = getPreferences(MODE_PRIVATE) ?: return
        val jsonArray = JSONArray()
        emergencyContacts.forEach {
            val jsonObject = JSONObject().apply { put("name", it.name); put("number", it.number) }
            jsonArray.put(jsonObject)
        }
        sharedPref.edit().putString("emergency_contacts", jsonArray.toString()).apply()
    }

    private fun loadSettings() {
        val sharedPref = getPreferences(MODE_PRIVATE)
        val contactsJson = sharedPref.getString("emergency_contacts", null)
        if (contactsJson != null) {
            try {
                val jsonArray = JSONArray(contactsJson)
                emergencyContacts.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    emergencyContacts.add(EmergencyContact(obj.getString("name"), obj.getString("number")))
                }
            } catch (e: Exception) {}
        }
        isDarkMode = sharedPref.getBoolean("dark_mode", false)
    }

    private fun saveSettings() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", isDarkMode).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSosCalling()
        stopEvidenceCollection()
    }
}

// UI Components
@Composable
fun HomeScreen(
    emergencyContacts: List<EmergencyContact>,
    isDarkMode: Boolean,
    isSosActive: Boolean,
    sosCountdown: Int,
    isTrackingTrip: Boolean,
    onToggleDarkMode: () -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (EmergencyContact) -> Unit,
    onSosClick: () -> Unit,
    onStopSos: () -> Unit,
    onNavigate: (String) -> Unit,
    onToggleTrip: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Scaffold(
        bottomBar = {
            BottomAppBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") },
                    selected = false,
                    onClick = { onNavigate("heatmap") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Report, contentDescription = "Report") },
                    label = { Text("Report") },
                    selected = false,
                    onClick = { onNavigate("report") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { onNavigate("settings") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SafeVoice", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggleDarkMode) {
                    Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SOS Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(if (isSosActive || sosCountdown > 0) Color.Red else MaterialTheme.colorScheme.primary)
                    .clickable { if (sosCountdown > 0 || isSosActive) onStopSos() else onSosClick() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val textColor = if (isDarkMode) Color.Black else Color.White
                    Text(
                        if (sosCountdown > 0) "$sosCountdown" else if (isSosActive) "STOP" else "SOS",
                        color = textColor,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (sosCountdown > 0) Text("Tap to Cancel", color = textColor.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Quick Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickActionItem(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    label = if (isTrackingTrip) "Stop Trip" else "Start Trip",
                    color = if (isTrackingTrip) Color.Red else Color.Green,
                    onClick = onToggleTrip
                )
                QuickActionItem(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery",
                    color = Color.Blue,
                    onClick = onOpenBatterySettings
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Emergency Contacts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Emergency Contacts", fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddContact) {
                    Icon(Icons.Default.Add, null)
                    Text("Add")
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(emergencyContacts) { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Medium)
                                Text(contact.number, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { onRemoveContact(contact) }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color)
        }
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MainViewModel, fusedLocationClient: FusedLocationProviderClient, onBack: () -> Unit) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val incidents by viewModel.allIncidents.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                userLocation = location?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(20.5937, 78.9629)
            }
        } else {
            userLocation = GeoPoint(20.5937, 78.9629)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = {
                MapView(it).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
            update = { view ->
                userLocation?.let {
                    view.controller.setCenter(it)
                }
                
                // Clear and add overlays for incidents
                view.overlays.clear()
                incidents.forEach { incident ->
                    // Add Heat Circle
                    val heatCircle = createHeatCircle(view, incident.latitude, incident.longitude)
                    view.overlays.add(heatCircle)
                    
                    // Add Marker
                    val marker = Marker(view)
                    marker.position = GeoPoint(incident.latitude, incident.longitude)
                    marker.title = incident.type
                    marker.snippet = incident.description
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    view.overlays.add(marker)
                }
                view.invalidate()
            }
        )
    }
}

private fun createHeatCircle(mapView: MapView, lat: Double, lng: Double): Polygon {
    val circle = Polygon(mapView)
    val circlePoints = Polygon.pointsAsCircle(GeoPoint(lat, lng), 40.0) // 40 meter radius
    
    circle.points = circlePoints
    // Red color with 30% transparency
    circle.fillPaint.color = Color.Red.copy(alpha = 0.3f).toArgb()
    circle.outlinePaint.strokeWidth = 0f // No border for a "glow" look
    return circle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: MainViewModel, fusedLocationClient: FusedLocationProviderClient, onBack: () -> Unit) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Harassment") }
    val incidentTypes = listOf("Harassment", "Suspicious Activity", "Poor Lighting", "Theft", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Incident") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("What would you like to report?", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(150.dp)
            ) {
                items(incidentTypes) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                viewModel.reportIncident(selectedType, it.latitude, it.longitude, description)
                                Toast.makeText(context, "Reported successfully", Toast.LENGTH_SHORT).show()
                                onBack() // Navigate back immediately for real-time feel
                            }
                        }
                    } else {
                        Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit Report")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenBattery: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            SettingsItem(
                title = "Battery Optimization",
                subtitle = "Disable optimization for better tracking",
                icon = Icons.Default.BatteryAlert,
                onClick = onOpenBattery
            )
            HorizontalDivider()
            SettingsItem(
                title = "About SafeVoice",
                subtitle = "Version 1.0.0",
                icon = Icons.Default.Info,
                onClick = {}
            )
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

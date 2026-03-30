SafeVoice 🛡️
AI-Powered Personal Safety & Real-Time Threat Mapping

SafeVoice is a modern Android application designed to provide users with immediate emergency assistance and crowdsourced situational awareness. By integrating voice-triggered SOS alerts and a localized, privacy-focused heatmap, SafeVoice empowers individuals to navigate their surroundings with confidence and security.

🚀 Key Features
 Voice-Activated SOS: Utilizes background services to listen for specific distress triggers, automatically capturing audio evidence and initiating emergency protocols.

 Real-Time Safety Heatmap: A local-first heatmap powered by OpenStreetMap (OSM). It visualizes reported incidents (harassment, poor lighting, etc.) using overlapping circular overlays to identify "danger zones" in real-time.

 Emergency Broadcasting: Automatically fetches high-accuracy GPS coordinates and broadcasts a distress message with a live location link to pre-defined emergency contacts via SMS.

 Privacy-Centric Mapping: Unlike proprietary solutions, SafeVoice utilizes OSM to ensure user location data is processed locally and not tracked by third-party advertising networks.

🛠️ Tech Stack
Language: Kotlin

 UI Framework: Jetpack Compose (Modern Declarative UI)

 Mapping Engine: Osmdroid (OpenStreetMap for Android)

 Architecture: MVVM (Model-View-ViewModel)

 Reactive Streams: Kotlin Coroutines & Flow for real-time heatmap updates.

 Local Database: Room Persistence Library.

🔐 Security & GSoC Focus (AFL++ Objectives)
In alignment with security research and open-source best practices, SafeVoice implements:

 Data Sovereignty: By migrating from Google Maps to OSM, the application eliminates "Leaky API" risks and proprietary tracking.

 Least Privilege Principle: The app requests sensitive permissions (Microphone/Location) only when the safety service is active.

 Secure Token Management: Repository management via GitHub Fine-grained Personal Access Tokens (PAT) to ensure secure CI/CD and push operations.

 Local-First Processing: Incident data is stored and rendered using local reactive streams to minimize the data exposure window during transit.

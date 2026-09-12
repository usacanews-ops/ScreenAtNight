package com.example.screenatnight

data class CityLocation(val name: String, val lat: Double, val lng: Double)

object CityDatabase {
    val CITIES = listOf(
        CityLocation("Custom / Use GPS Location", 0.0, 0.0),
        CityLocation("New Delhi (India)", 28.6139, 77.2090),
        CityLocation("Mumbai (India)", 19.0760, 72.8777),
        CityLocation("Kolkata (India)", 22.5726, 88.3639),
        CityLocation("Chennai (India)", 13.0827, 80.2707),
        CityLocation("Bengaluru (India)", 12.9716, 77.5946),
        CityLocation("Chandigarh (India)", 30.7333, 76.7794),
        CityLocation("Amritsar (India)", 31.6340, 74.8723),
        CityLocation("London (UK)", 51.5074, -0.1278),
        CityLocation("New York (USA)", 40.7128, -74.0060),
        CityLocation("Los Angeles (USA)", 34.0522, -118.2437),
        CityLocation("Toronto (Canada)", 43.6532, -79.3832),
        CityLocation("Dubai (UAE)", 25.2048, 55.2708),
        CityLocation("Singapore", 1.3521, 103.8198),
        CityLocation("Tokyo (Japan)", 35.6762, 139.6503),
        CityLocation("Sydney (Australia)", -33.8688, 151.2093)
    )
}

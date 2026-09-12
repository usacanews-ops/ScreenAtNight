package com.example.screenatnight

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var switchOverlay: MaterialSwitch
    private lateinit var seekBarOpacity: SeekBar
    private lateinit var tvOpacityLabel: TextView
    private lateinit var switchSchedule: MaterialSwitch
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var switchSolar: MaterialSwitch
    private lateinit var spinnerCities: Spinner
    private lateinit var etSunsetOffset: EditText
    private lateinit var etSunriseOffset: EditText
    private lateinit var btnSyncSolar: Button
    private lateinit var tvSolarInfo: TextView

    private val prefs by lazy { getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE) }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadPreferences()
        setupListeners()
    }

    private fun bindViews() {
        switchOverlay = findViewById(R.id.switchOverlay)
        seekBarOpacity = findViewById(R.id.seekBarOpacity)
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel)
        switchSchedule = findViewById(R.id.switchSchedule)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        switchSolar = findViewById(R.id.switchSolar)
        spinnerCities = findViewById(R.id.spinnerCities)
        etSunsetOffset = findViewById(R.id.etSunsetOffset)
        etSunriseOffset = findViewById(R.id.etSunriseOffset)
        btnSyncSolar = findViewById(R.id.btnSyncSolar)
        tvSolarInfo = findViewById(R.id.tvSolarInfo)

        val cityNames = CityDatabase.CITIES.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cityNames)
        spinnerCities.adapter = adapter
    }

    private fun loadPreferences() {
        val opacity = prefs.getInt("opacity", 40)
        seekBarOpacity.progress = opacity
        tvOpacityLabel.text = "Darkness Level: $opacity%"

        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        switchSolar.isChecked = prefs.getBoolean("solar_enabled", false)

        val savedCityIndex = prefs.getInt("selected_city_index", 1)
        spinnerCities.setSelection(savedCityIndex)

        val startHour = prefs.getInt("start_hour", 22)
        val startMin = prefs.getInt("start_min", 0)
        val endHour = prefs.getInt("end_hour", 6)
        val endMin = prefs.getInt("end_min", 0)

        btnStartTime.text = String.format(Locale.getDefault(), "Turn On: %02d:%02d", startHour, startMin)
        btnEndTime.text = String.format(Locale.getDefault(), "Turn Off: %02d:%02d", endHour, endMin)

        etSunsetOffset.setText(prefs.getInt("sunset_offset", 0).toString())
        etSunriseOffset.setText(prefs.getInt("sunrise_offset", 0).toString())
    }

    private fun setupListeners() {
        switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkOverlayPermission()) {
                    startOverlayService()
                } else {
                    switchOverlay.isChecked = false
                    requestOverlayPermission()
                }
            } else {
                stopOverlayService()
            }
        }

        seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacityLabel.text = "Darkness Level: $progress%"
                prefs.edit().putInt("opacity", progress).apply()
                if (switchOverlay.isChecked) {
                    val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_SET_OPACITY
                        putExtra(OverlayService.EXTRA_OPACITY, progress)
                    }
                    startService(intent)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndTime.setOnClickListener { showTimePicker(false) }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("schedule_enabled", isChecked).apply()
            if (isChecked) scheduleAlarms()
        }

        switchSolar.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("solar_enabled", isChecked).apply()
            if (isChecked) checkLocationAndSyncSolar()
        }

        btnSyncSolar.setOnClickListener { checkLocationAndSyncSolar() }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        prefs.edit().putBoolean("is_active", true).apply()
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        prefs.edit().putBoolean("is_active", false).apply()
    }

    private fun showTimePicker(isStart: Boolean) {
        val currentHour = if (isStart) prefs.getInt("start_hour", 22) else prefs.getInt("end_hour", 6)
        val currentMin = if (isStart) prefs.getInt("start_min", 0) else prefs.getInt("end_min", 0)

        TimePickerDialog(this, { _, h, m ->
            val label = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            if (isStart) {
                btnStartTime.text = "Turn On: $label"
                prefs.edit().putInt("start_hour", h).putInt("start_min", m).apply()
            } else {
                btnEndTime.text = "Turn Off: $label"
                prefs.edit().putInt("end_hour", h).putInt("end_min", m).apply()
            }
            if (switchSchedule.isChecked || switchSolar.isChecked) scheduleAlarms()
        }, currentHour, currentMin, true).show()
    }

    private fun scheduleAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("start_hour", 22))
            set(Calendar.MINUTE, prefs.getInt("start_min", 0))
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("end_hour", 6))
            set(Calendar.MINUTE, prefs.getInt("end_min", 0))
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        val onIntent = Intent(this, ScheduleReceiver::class.java).apply { action = ScheduleReceiver.ACTION_ON }
        val offIntent = Intent(this, ScheduleReceiver::class.java).apply { action = ScheduleReceiver.ACTION_OFF }

        val pOn = PendingIntent.getBroadcast(this, 101, onIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pOff = PendingIntent.getBroadcast(this, 102, offIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, pOn)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, pOff)
    }

    private fun checkLocationAndSyncSolar() {
        val selectedIndex = spinnerCities.selectedItemPosition
        prefs.edit().putInt("selected_city_index", selectedIndex).apply()

        if (selectedIndex == 0) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 200)
                return
            }
            val locManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            val lat = loc?.latitude ?: 28.6139
            val lng = loc?.longitude ?: 77.2090
            applySolarTimes(lat, lng)
        } else {
            val city = CityDatabase.CITIES[selectedIndex]
            applySolarTimes(city.lat, city.lng)
        }
    }

    private fun applySolarTimes(lat: Double, lng: Double) {
        val sunsetOffset = etSunsetOffset.text.toString().toIntOrNull() ?: 0
        val sunriseOffset = etSunriseOffset.text.toString().toIntOrNull() ?: 0

        prefs.edit()
            .putInt("sunset_offset", sunsetOffset)
            .putInt("sunrise_offset", sunriseOffset)
            .apply()

        val solar = SolarHelper.calculate(lat, lng)

        val turnOnTime = Calendar.getInstance().apply {
            timeInMillis = solar.sunsetMillis + (sunsetOffset * 60 * 1000)
        }
        val turnOffTime = Calendar.getInstance().apply {
            timeInMillis = solar.sunriseMillis + (sunriseOffset * 60 * 1000)
        }

        val onHour = turnOnTime.get(Calendar.HOUR_OF_DAY)
        val onMin = turnOnTime.get(Calendar.MINUTE)
        val offHour = turnOffTime.get(Calendar.HOUR_OF_DAY)
        val offMin = turnOffTime.get(Calendar.MINUTE)

        prefs.edit()
            .putInt("start_hour", onHour)
            .putInt("start_min", onMin)
            .putInt("end_hour", offHour)
            .putInt("end_min", offMin)
            .apply()

        btnStartTime.text = String.format(Locale.getDefault(), "Turn On: %02d:%02d", onHour, onMin)
        btnEndTime.text = String.format(Locale.getDefault(), "Turn Off: %02d:%02d", offHour, offMin)

        tvSolarInfo.text = "Synced: Sunset ${timeFormat.format(Date(solar.sunsetMillis))}, Sunrise ${timeFormat.format(Date(solar.sunriseMillis))}"
        if (switchSolar.isChecked) scheduleAlarms()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationAndSyncSolar()
        }
    }
}

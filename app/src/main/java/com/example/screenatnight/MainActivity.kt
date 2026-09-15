package com.example.screenatnight

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var switchOverlay: MaterialSwitch
    private lateinit var seekBarOpacity: SeekBar
    private lateinit var tvOpacityLabel: TextView
    private lateinit var switchSchedule: MaterialSwitch
    private lateinit var btnStartTime: MaterialButton
    private lateinit var btnEndTime: MaterialButton
    private lateinit var switchSolar: MaterialSwitch
    private lateinit var spinnerCities: Spinner
    private lateinit var etSunsetOffset: EditText
    private lateinit var etSunriseOffset: EditText
    private lateinit var btnSyncSolar: MaterialButton
    private lateinit var tvSolarInfo: TextView
    private lateinit var tvFooterBranding: TextView

    private val prefs by lazy { getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE) }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "opacity" -> {
                val updated = prefs.getInt("opacity", 40)
                seekBarOpacity.progress = updated
                tvOpacityLabel.text = "Darkness Level: $updated%"
            }
            "is_active" -> {
                switchOverlay.isChecked = prefs.getBoolean("is_active", false)
            }
            "start_hour", "start_min", "end_hour", "end_min" -> {
                updateTimeButtonsDisplay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadPreferences()
        setupListeners()
        requestNotificationPermission()
        checkExactAlarmPermission()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        switchOverlay.isChecked = prefs.getBoolean("is_active", false)
        val op = prefs.getInt("opacity", 40)
        seekBarOpacity.progress = op
        tvOpacityLabel.text = "Darkness Level: $op%"
        updateTimeButtonsDisplay()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
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
        tvFooterBranding = findViewById(R.id.tvFooterBranding)

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

        etSunsetOffset.setText(prefs.getInt("sunset_offset", 0).toString())
        etSunriseOffset.setText(prefs.getInt("sunrise_offset", 0).toString())

        updateTimeButtonsDisplay()

        val savedInfo = prefs.getString("last_solar_info", null)
        if (!savedInfo.isNullOrEmpty()) {
            tvSolarInfo.text = savedInfo
        }
    }

    private fun updateTimeButtonsDisplay() {
        val startHour = prefs.getInt("start_hour", 22)
        val startMin = prefs.getInt("start_min", 0)
        val endHour = prefs.getInt("end_hour", 6)
        val endMin = prefs.getInt("end_min", 0)

        btnStartTime.text = String.format(Locale.getDefault(), "On: %02d:%02d", startHour, startMin)
        btnEndTime.text = String.format(Locale.getDefault(), "Off: %02d:%02d", endHour, endMin)
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
                if (fromUser) {
                    prefs.edit().putInt("opacity", progress).apply()
                    if (switchOverlay.isChecked) {
                        val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_SET_OPACITY
                            putExtra(OverlayService.EXTRA_OPACITY, progress)
                        }
                        startService(intent)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndTime.setOnClickListener { showTimePicker(false) }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("schedule_enabled", isChecked).apply()
            if (isChecked) {
                switchSolar.isChecked = false
                prefs.edit().putBoolean("solar_enabled", false).apply()
                scheduleAlarms()
                evaluateCurrentState()
                Toast.makeText(this, "Fixed schedule enabled", Toast.LENGTH_SHORT).show()
            } else {
                if (!switchSolar.isChecked) cancelAlarms()
            }
        }

        switchSolar.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("solar_enabled", isChecked).apply()
            if (isChecked) {
                switchSchedule.isChecked = false
                prefs.edit().putBoolean("schedule_enabled", false).apply()
                checkLocationAndSyncSolar()
            } else {
                if (!switchSchedule.isChecked) cancelAlarms()
            }
        }

        btnSyncSolar.setOnClickListener { checkLocationAndSyncSolar() }

        tvFooterBranding.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://itwebsolutions.ca"))
            startActivity(browserIntent)
        }
    }

    /**
     * Resolves Issue 1: Checks if current time is inside the scheduled window right now.
     * Supports both same-day (e.g., 14:00 to 17:00) and overnight (e.g., 22:30 to 05:30) ranges.
     */
    private fun evaluateCurrentState() {
        val startHour = prefs.getInt("start_hour", 22)
        val startMin = prefs.getInt("start_min", 0)
        val endHour = prefs.getInt("end_hour", 6)
        val endMin = prefs.getInt("end_min", 0)

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMin
        val endMinutes = endHour * 60 + endMin

        val isInsideWindow = if (startMinutes <= endMinutes) {
            // Same day window
            currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight window spanning past midnight (e.g., 22:30 to 05:30)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }

        if (isInsideWindow) {
            if (!switchOverlay.isChecked && checkOverlayPermission()) {
                startOverlayService()
                switchOverlay.isChecked = true
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 300)
            }
        }
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
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SET_OPACITY
            putExtra(OverlayService.EXTRA_OPACITY, prefs.getInt("opacity", 40))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        prefs.edit().putBoolean("is_active", true).apply()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP_SERVICE
        }
        stopService(intent)
        prefs.edit().putBoolean("is_active", false).apply()
    }

    private fun showTimePicker(isStart: Boolean) {
        val currentHour = if (isStart) prefs.getInt("start_hour", 22) else prefs.getInt("end_hour", 6)
        val currentMin = if (isStart) prefs.getInt("start_min", 0) else prefs.getInt("end_min", 0)

        TimePickerDialog(this, { _, h, m ->
            if (isStart) {
                prefs.edit().putInt("start_hour", h).putInt("start_min", m).apply()
            } else {
                prefs.edit().putInt("end_hour", h).putInt("end_min", m).apply()
            }
            updateTimeButtonsDisplay()
            if (switchSchedule.isChecked || switchSolar.isChecked) {
                scheduleAlarms()
                evaluateCurrentState()
            }
        }, currentHour, currentMin, true).show()
    }

    private fun scheduleAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("start_hour", 22))
            set(Calendar.MINUTE, prefs.getInt("start_min", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DATE, 1)
        }

        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("end_hour", 6))
            set(Calendar.MINUTE, prefs.getInt("end_min", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DATE, 1)
        }

        val onIntent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_ON
            setPackage(packageName)
        }
        val offIntent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_OFF
            setPackage(packageName)
        }

        val pOn = PendingIntent.getBroadcast(this, 101, onIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pOff = PendingIntent.getBroadcast(this, 102, offIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, pOn)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, pOff)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, pOn)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, pOff)
        }
    }

    private fun cancelAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val onIntent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_ON
            setPackage(packageName)
        }
        val offIntent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_OFF
            setPackage(packageName)
        }

        val pOn = PendingIntent.getBroadcast(this, 101, onIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        val pOff = PendingIntent.getBroadcast(this, 102, offIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

        if (pOn != null) alarmManager.cancel(pOn)
        if (pOff != null) alarmManager.cancel(pOff)
    }

    private fun checkLocationAndSyncSolar() {
        val selectedIndex = spinnerCities.selectedItemPosition
        prefs.edit().putInt("selected_city_index", selectedIndex).apply()

        if (selectedIndex == 0) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 200)
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

    /**
     * Resolves Issue 2: Calculates solar times, stores coordinates for midnight updates,
     * updates the UI to show exact active schedule and offsets.
     */
    private fun applySolarTimes(lat: Double, lng: Double) {
        val sunsetOffset = etSunsetOffset.text.toString().toIntOrNull() ?: 0
        val sunriseOffset = etSunriseOffset.text.toString().toIntOrNull() ?: 0

        prefs.edit()
            .putFloat("solar_lat", lat.toFloat())
            .putFloat("solar_lng", lng.toFloat())
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

        updateTimeButtonsDisplay()

        val rawSunset = timeFormat.format(Date(solar.sunsetMillis))
        val rawSunrise = timeFormat.format(Date(solar.sunriseMillis))
        val scheduledOn = String.format(Locale.getDefault(), "%02d:%02d", onHour, onMin)
        val scheduledOff = String.format(Locale.getDefault(), "%02d:%02d", offHour, offMin)

        val infoString = "Sun: Sunset $rawSunset, Sunrise $rawSunrise\nEffective Schedule: On at $scheduledOn, Off at $scheduledOff"
        tvSolarInfo.text = infoString
        prefs.edit().putString("last_solar_info", infoString).apply()

        scheduleDailyMidnightCheck()

        if (switchSolar.isChecked) {
            scheduleAlarms()
            evaluateCurrentState()
            Toast.makeText(this, "Solar schedule applied & synced", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleDailyMidnightCheck() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_RECALCULATE_SOLAR
            setPackage(packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            103,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pendingIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationAndSyncSolar()
        }
    }
}

package com.example.screenatnight

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object SolarHelper {

    data class SolarTimes(val sunriseMillis: Long, val sunsetMillis: Long)

    fun calculate(lat: Double, lng: Double, calendar: Calendar = Calendar.getInstance()): SolarTimes {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val sunriseHour = computeTime(dayOfYear, lat, lng, isSunrise = true)
        val sunsetHour = computeTime(dayOfYear, lat, lng, isSunrise = false)

        val baseCal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val sunriseMillis = baseCal.timeInMillis + (sunriseHour * 3600 * 1000).toLong()
        val sunsetMillis = baseCal.timeInMillis + (sunsetHour * 3600 * 1000).toLong()

        return SolarTimes(sunriseMillis, sunsetMillis)
    }

    private fun computeTime(dayOfYear: Int, lat: Double, lng: Double, isSunrise: Boolean): Double {
        val zenith = 90.8333
        val lngHour = lng / 15.0

        val t = if (isSunrise) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = (l % 360 + 360) % 360

        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = (ra % 360 + 360) % 360
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(lat)))) /
                (cosDec * cos(Math.toRadians(lat)))

        val h = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
        } else {
            Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
        } / 15.0

        val tLocal = h + ra - (0.06571 * t) - 6.622
        val utcTime = (tLocal - lngHour) % 24.0

        val offsetHours = TimeZone.getDefault().rawOffset / (1000.0 * 3600.0)
        return (utcTime + offsetHours + 24.0) % 24.0
    }
}

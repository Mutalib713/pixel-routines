package com.mosman.routines

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise/sunset times from the standard NOAA algorithm — pure math, works offline.
 * Good to within a minute or two, which is plenty for "dark theme at sunset".
 */
object SunCalc {

    /** Next occurrence of this Sun trigger after [from], or null near the poles. */
    fun next(t: Trigger.Sun, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        var date = from.toLocalDate()
        repeat(4) {
            eventAt(date, t.lat, t.lng, t.sunrise)?.let { event ->
                val local = event.atZone(from.zone).plusMinutes(t.offsetMin.toLong())
                if (local.isAfter(from)) return local
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** Sunrise or sunset on [date] at lat/lng as an Instant, or null (polar day/night). */
    fun eventAt(date: LocalDate, lat: Double, lng: Double, sunrise: Boolean): Instant? {
        val n = date.toEpochDay() - LocalDate.of(2000, 1, 1).toEpochDay() + 0.0008
        val jStar = n - lng / 360.0                                  // mean solar time
        val m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360.0)   // solar mean anomaly
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m) // equation of center
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372) % 360.0) // ecliptic longitude
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val delta = asin(sin(lambda) * sin(Math.toRadians(23.4397)))      // sun declination
        val latR = Math.toRadians(lat)
        // Hour angle for the sun 0.833° below the horizon (refraction + solar radius)
        val cosH = (sin(Math.toRadians(-0.833)) - sin(latR) * sin(delta)) / (cos(latR) * cos(delta))
        if (cosH < -1.0 || cosH > 1.0) return null
        val h = Math.toDegrees(acos(cosH)) / 360.0
        val jEvent = if (sunrise) jTransit - h else jTransit + h
        // Julian date → epoch millis
        val millis = ((jEvent - 2440587.5) * 86400000.0).toLong()
        return Instant.ofEpochMilli(millis)
    }
}

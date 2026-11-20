package com.mosman.routines

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The five daily prayers. Enum names are saved in routines: never rename them (Sacred Rule 2). */
enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA;
    fun label() = when (this) { FAJR -> "Fajr"; DHUHR -> "Dhuhr"; ASR -> "Asr"; MAGHRIB -> "Maghrib"; ISHA -> "Isha" }
}

/**
 * How far below the horizon the sun is for Fajr and Isha. Different bodies use different angles,
 * which is why apps disagree by a few minutes. Ghana sites commonly use Muslim World League.
 * Enum names are saved: never rename them.
 */
enum class PrayerMethod(val fajrAngle: Double, val ishaAngle: Double?, val ishaMinutes: Int?) {
    MWL(18.0, 17.0, null),
    EGYPT(19.5, 17.5, null),
    KARACHI(18.0, 18.0, null),
    ISNA(15.0, 15.0, null),
    UMM_AL_QURA(18.5, null, 90);

    fun label() = when (this) {
        MWL -> "Muslim World League"
        EGYPT -> "Egyptian Authority"
        KARACHI -> "Karachi (Islamic Sciences)"
        ISNA -> "ISNA (North America)"
        UMM_AL_QURA -> "Umm al-Qura (Makkah)"
    }
}

/** Asr starts when a shadow is 1x (Shafi'i, Maliki, Hanbali) or 2x (Hanafi) an object's length. */
enum class AsrSchool(val shadow: Int) { SHAFII(1), HANAFI(2);
    fun label() = if (this == SHAFII) "Standard (Shafi'i)" else "Hanafi"
}

/**
 * Prayer times from the sun's position: pure maths, works offline, no web search.
 * Uses the same solar formulas as [SunCalc] (sunrise/sunset), plus the sun-below-horizon angles
 * for Fajr and Isha and the shadow-length rule for Asr. Checked against Aladhan for Kumasi
 * (see PrayerCalcTest) to within 2 minutes.
 *
 * What it can't know: your mosque's own congregation (jama'ah) times, which are often set a few
 * minutes after the calculated time. Use the offset for that.
 */
object PrayerCalc {

    data class Day(val times: Map<Prayer, ZonedDateTime>)

    fun day(date: LocalDate, lat: Double, lng: Double, zone: ZoneId,
            method: PrayerMethod = PrayerMethod.MWL, school: AsrSchool = AsrSchool.SHAFII): Day? {
        val n = date.toEpochDay() - LocalDate.of(2000, 1, 1).toEpochDay() + 0.0008
        val jStar = n - lng / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360.0)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372) % 360.0)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val delta = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val latR = Math.toRadians(lat)

        /** Julian date when the sun is at [altitudeDeg], before (morning) or after noon. */
        fun at(altitudeDeg: Double, morning: Boolean): Double? {
            val cosH = (sin(Math.toRadians(altitudeDeg)) - sin(latR) * sin(delta)) / (cos(latR) * cos(delta))
            if (cosH < -1.0 || cosH > 1.0) return null
            val h = Math.toDegrees(acos(cosH)) / 360.0
            return if (morning) jTransit - h else jTransit + h
        }

        val asrAltitude = Math.toDegrees(atan(1.0 / (school.shadow + tan(abs(latR - delta)))))
        val maghrib = at(-0.833, morning = false) ?: return null
        val isha = method.ishaAngle?.let { at(-it, morning = false) }
            ?: method.ishaMinutes?.let { maghrib + it / 1440.0 } ?: return null
        val j = mapOf(
            Prayer.FAJR to (at(-method.fajrAngle, morning = true) ?: return null),
            Prayer.DHUHR to jTransit,
            Prayer.ASR to (at(asrAltitude, morning = false) ?: return null),
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha,
        )
        return Day(j.mapValues { (_, jd) ->
            Instant.ofEpochMilli(((jd - 2440587.5) * 86400000.0).toLong()).atZone(zone)
        })
    }

    /** The next time this prayer trigger should fire after [from] (offset included). */
    fun next(t: Trigger.Prayer, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        var date = from.toLocalDate()
        repeat(4) {
            day(date, t.lat, t.lng, from.zone, t.method, t.school)?.times?.get(t.prayer)?.let { time ->
                val at = time.plusMinutes(t.offsetMin.toLong())
                if (at.isAfter(from)) return at
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** Ghana's regional capitals, so the chat can answer "prayer times in Kumasi" offline. */
    val GHANA_CITIES: Map<String, Pair<Double, Double>> = mapOf(
        "Kumasi" to (6.6885 to -1.6244),
        "Accra" to (5.6037 to -0.1870),
        "Tamale" to (9.4008 to -0.8393),
        "Takoradi" to (4.8845 to -1.7554),
        "Cape Coast" to (5.1053 to -1.2466),
        "Ho" to (6.6008 to 0.4713),
        "Sunyani" to (7.3349 to -2.3123),
        "Koforidua" to (6.0941 to -0.2591),
        "Bolgatanga" to (10.7856 to -0.8514),
        "Wa" to (10.0601 to -2.5099),
    )
}

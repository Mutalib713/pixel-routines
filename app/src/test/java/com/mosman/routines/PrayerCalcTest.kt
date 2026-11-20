package com.mosman.routines

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reference times for Kumasi (6.6885, -1.6244) from api.aladhan.com, fetched 2026-11-20.
 * Our maths must land within 2 minutes of them.
 */
class PrayerCalcTest {
    private val zone = ZoneId.of("Africa/Accra")
    private val kumasi = 6.6885 to -1.6244

    private fun check(date: String, method: PrayerMethod, school: AsrSchool, expected: Map<Prayer, String>) {
        val day = PrayerCalc.day(LocalDate.parse(date), kumasi.first, kumasi.second, zone, method, school)!!
        expected.forEach { (p, hhmm) ->
            val got = day.times.getValue(p).toLocalTime()
            val diff = Duration.between(LocalTime.parse(hhmm), got).abs().toMinutes()
            assertTrue("$date ${method.name} ${p.label()}: expected $hhmm, got $got", diff <= 2)
        }
    }

    @Test fun mwlShafii_20Nov2026() = check("2026-11-20", PrayerMethod.MWL, AsrSchool.SHAFII, mapOf(
        Prayer.FAJR to "04:45", Prayer.DHUHR to "11:52", Prayer.ASR to "15:14", Prayer.MAGHRIB to "17:46", Prayer.ISHA to "18:55"))

    @Test fun mwlHanafiAsr_20Nov2026() = check("2026-11-20", PrayerMethod.MWL, AsrSchool.HANAFI, mapOf(
        Prayer.ASR to "16:08"))

    @Test fun egypt_20Nov2026() = check("2026-11-20", PrayerMethod.EGYPT, AsrSchool.SHAFII, mapOf(
        Prayer.FAJR to "04:39", Prayer.ISHA to "18:57"))

    @Test fun isna_20Nov2026() = check("2026-11-20", PrayerMethod.ISNA, AsrSchool.SHAFII, mapOf(
        Prayer.FAJR to "04:58", Prayer.ISHA to "18:46"))

    @Test fun mwl_21Jun2026() = check("2026-06-21", PrayerMethod.MWL, AsrSchool.SHAFII, mapOf(
        Prayer.FAJR to "04:36", Prayer.DHUHR to "12:08", Prayer.ASR to "15:36", Prayer.MAGHRIB to "18:24", Prayer.ISHA to "19:36"))

    @Test fun mwl_1Jan2027() = check("2027-01-01", PrayerMethod.MWL, AsrSchool.SHAFII, mapOf(
        Prayer.FAJR to "05:03", Prayer.DHUHR to "12:10", Prayer.ASR to "15:31", Prayer.MAGHRIB to "18:02", Prayer.ISHA to "19:13"))
}

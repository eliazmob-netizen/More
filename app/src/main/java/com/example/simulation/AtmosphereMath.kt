package com.example.simulation

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object AtmosphereMath {
    const val D = PI / 180.0
    const val RE = 6371e3
    const val RA = 6471e3
    const val HR = 8e3
    const val HM = 1.2e3
    val BR = doubleArrayOf(5.8e-6, 13.5e-6, 33.1e-6)
    const val BM = 21e-6

    const val LW = 64
    const val LH = 32

    fun clamp(v: Double, a: Double, b: Double): Double = min(b, max(a, v))

    fun wrapDeg(a: Double): Double = ((a + 540.0) % 360.0 + 360.0) % 360.0 - 180.0

    fun formatHours(t: Double): String {
        val m = (((t * 60.0).let { Math.round(it) }.toInt() % 1440) + 1440) % 1440
        val hh = (m / 60).toString().padStart(2, '0')
        val mm = (m % 60).toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun hoursNow(utcOffsetSeconds: Int?, nowMs: Long = System.currentTimeMillis()): Double {
        return if (utcOffsetSeconds != null) {
            (((nowMs / 3_600_000.0) + utcOffsetSeconds / 3600.0) % 24.0 + 24.0) % 24.0
        } else {
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMs
            cal.get(Calendar.HOUR_OF_DAY) +
                cal.get(Calendar.MINUTE) / 60.0 +
                cal.get(Calendar.SECOND) / 3600.0
        }
    }

    fun doyNow(nowMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = nowMs
        return cal.get(Calendar.DAY_OF_YEAR)
    }

    fun solar(h: Double, lat: Double, doy: Int): SolarPos {
        val dec = 23.44 * D * sin(2.0 * PI * (doy - 81) / 365.0)
        val ha = (h - 12.0) * 15.0 * D
        val la = lat * D
        val sinEl = sin(la) * sin(dec) + cos(la) * cos(dec) * cos(ha)
        val el = asin(clamp(sinEl, -1.0, 1.0))
        val az = atan2(sin(ha), cos(ha) * sin(la) - tan(dec) * cos(la))
        return SolarPos(el, az)
    }

    fun sunTimes(lat: Double, doy: Int): SunTimes {
        var up: Double? = null
        var dn: Double? = null
        var h = 0.05
        while (h <= 24.0001) {
            val e = solar(h, lat, doy).el
            val pv = solar(h - 0.05, lat, doy).el
            if (pv < 0.0 && e >= 0.0 && up == null) up = h
            if (pv >= 0.0 && e < 0.0) dn = h
            h += 0.05
        }
        return SunTimes(up ?: 6.0, dn ?: 20.0)
    }

    fun findSunsetLighthouseHour(lat: Double, doy: Int, targetCamDeg: Double = 263.0): Double {
        val targetAz = (targetCamDeg - 180.0) * D
        var bestH = 18.0
        var bestScore = Double.MAX_VALUE
        var h = 12.0
        while (h <= 23.8) {
            val sp = solar(h, lat, doy)
            val elDeg = sp.el / D
            if (elDeg in 2.0..18.0) {
                val azErr = abs(wrapDeg((sp.az - targetAz) / D))
                val elPenalty = abs(elDeg - 7.5) * 0.4
                val score = azErr + elPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestH = h
                }
            }
            h += 0.05
        }
        return bestH
    }

    fun findNightMoonLighthouseAlignment(
        lat: Double,
        doy: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<Double, Double> {
        val ph = moonPhase(nowMs)
        val la = lat * D
        val dm = 5.0 * D
        val lhWorldAz = 268.0
        var bestH = 23.0
        var bestCam = 263.0
        var bestScore = Double.MAX_VALUE
        var h = 0.0
        while (h < 24.0) {
            val sp = solar(h, lat, doy)
            if (sp.el / D < -2.0) {
                val ha = (h - 12.0) * 15.0 * D - ph * 2.0 * PI
                val sE = sin(la) * sin(dm) + cos(la) * cos(dm) * cos(ha)
                val mElDeg = asin(clamp(sE, -1.0, 1.0)) / D
                if (mElDeg > 3.5) {
                    val mAz = atan2(sin(ha), cos(ha) * sin(la) - tan(dm) * cos(la))
                    val mAzCompass = ((mAz / D + 180.0) % 360.0 + 360.0) % 360.0
                    val diff = abs(wrapDeg(mAzCompass - lhWorldAz))
                    val score = diff + abs(mElDeg - 10.0) * 0.25
                    if (score < bestScore) {
                        bestScore = score
                        bestH = h
                        val midOffset = clamp(wrapDeg(mAzCompass - lhWorldAz) * 0.5, -8.0, 8.0)
                        bestCam = ((lhWorldAz + midOffset - 3.0) % 360.0 + 360.0) % 360.0
                    }
                }
            }
            h += 0.05
        }
        return bestH to bestCam
    }

    fun camVec(el: Double, az: Double, camAz: Double): DoubleArray {
        val a = az - camAz
        val cosEl = cos(el)
        return doubleArrayOf(sin(a) * cosEl, sin(el), cos(a) * cosEl)
    }

    private inline fun raySphereFirstExit(
        ox: Double, oy: Double, oz: Double,
        dx: Double, dy: Double, dz: Double,
        r: Double,
        outPair: DoubleArray,
    ) {
        val b = ox * dx + oy * dy + oz * dz
        val c = ox * ox + oy * oy + oz * oz - r * r
        val q = b * b - c
        if (q < 0.0) {
            outPair[0] = 1e5
            outPair[1] = -1e5
        } else {
            val e = sqrt(q)
            outPair[0] = -b - e
            outPair[1] = -b + e
        }
    }

    fun rsi(o: DoubleArray, d: DoubleArray, r: Double): DoubleArray {
        val out = DoubleArray(2)
        raySphereFirstExit(o[0], o[1], o[2], d[0], d[1], d[2], r, out)
        return out
    }

    fun atmos(rd: DoubleArray, sd: DoubleArray): DoubleArray {
        val out = DoubleArray(3)
        atmosInto(rd[0], rd[1], rd[2], sd[0], sd[1], sd[2], out, DoubleArray(2))
        return out
    }

    private fun atmosInto(
        rdx: Double, rdy: Double, rdz: Double,
        sdx: Double, sdy: Double, sdz: Double,
        out: DoubleArray,
        pairTmp: DoubleArray,
    ) {
        val roy = RE + 1.5
        raySphereFirstExit(0.0, roy, 0.0, rdx, rdy, rdz, RA, pairTmp)
        var tmax = pairTmp[1]
        raySphereFirstExit(0.0, roy, 0.0, rdx, rdy, rdz, RE, pairTmp)
        if (pairTmp[0] > 0.0) tmax = min(tmax, pairTmp[0])
        val ds = tmax / 10.0
        val mu = rdx * sdx + rdy * sdy + rdz * sdz
        val g = 0.76
        val phR = 3.0 / (16.0 * PI) * (1.0 + mu * mu)
        val phM = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) /
            ((2.0 + g * g) * (1.0 + g * g - 2.0 * g * mu).pow(1.5))

        var sR0 = 0.0
        var sR1 = 0.0
        var sR2 = 0.0
        var sM0 = 0.0
        var sM1 = 0.0
        var sM2 = 0.0
        var oR = 0.0
        var oM = 0.0
        var t = 0.0
        val br0 = BR[0]
        val br1 = BR[1]
        val br2 = BR[2]

        for (i in 0 until 10) {
            val midT = t + ds * 0.5
            val px = rdx * midT
            val py = roy + rdy * midT
            val pz = rdz * midT
            val h = hypot3(px, py, pz) - RE
            val hr = exp(-h / HR) * ds
            val hm = exp(-h / HM) * ds
            oR += hr
            oM += hm

            raySphereFirstExit(px, py, pz, sdx, sdy, sdz, RA, pairTmp)
            val dl = pairTmp[1] / 5.0
            var lR = 0.0
            var lM = 0.0
            var ok = true
            for (j in 0 until 5) {
                val step = dl * (j + 0.5)
                val qx = px + sdx * step
                val qy = py + sdy * step
                val qz = pz + sdz * step
                val hq = hypot3(qx, qy, qz) - RE
                if (hq < 0.0) {
                    ok = false
                    break
                }
                lR += exp(-hq / HR) * dl
                lM += exp(-hq / HM) * dl
            }
            if (ok) {
                val sumR = oR + lR
                val sumM = BM * 1.1 * (oM + lM)
                val at0 = exp(-(br0 * sumR + sumM))
                val at1 = exp(-(br1 * sumR + sumM))
                val at2 = exp(-(br2 * sumR + sumM))
                sR0 += at0 * hr
                sR1 += at1 * hr
                sR2 += at2 * hr
                sM0 += at0 * hm
                sM1 += at1 * hm
                sM2 += at2 * hm
            }
            t += ds
        }
        out[0] = 14.0 * (sR0 * br0 * phR + sM0 * BM * phM)
        out[1] = 14.0 * (sR1 * br1 * phR + sM1 * BM * phM)
        out[2] = 14.0 * (sR2 * br2 * phR + sM2 * BM * phM)
    }

    fun trans(sd: DoubleArray): DoubleArray {
        if (sd[1] < -0.12) return doubleArrayOf(0.0, 0.0, 0.0)
        val roy = RE + 1.5
        val pair = DoubleArray(2)
        raySphereFirstExit(0.0, roy, 0.0, sd[0], sd[1], sd[2], RA, pair)
        val dl = pair[1] / 8.0
        var oR = 0.0
        var oM = 0.0
        for (j in 0 until 8) {
            val step = dl * (j + 0.5)
            val qx = sd[0] * step
            val qy = roy + sd[1] * step
            val qz = sd[2] * step
            val hq = max(hypot3(qx, qy, qz) - RE, 0.0)
            oR += exp(-hq / HR) * dl
            oM += exp(-hq / HM) * dl
        }
        return DoubleArray(3) { k -> exp(-(BR[k] * oR + BM * 1.1 * oM)) }
    }

    private val nightBias = doubleArrayOf(0.00015, 0.00025, 0.0006)

    fun bakeRow(sd: DoubleArray, ex: Double, out: ByteArray, j: Int) {
        val dayW = clamp(sd[1] * 3.0, 0.0, 1.0)
        val v = (j + 0.5) / LH
        val el = v * v * PI / 2.0
        val ec = if (el < 0.035) 0.035 + el * 0.35 else el
        val cosEc = cos(ec)
        val sinEc = sin(ec)
        val expHorizon = exp(-el * 3.5) * dayW
        val cTmp = DoubleArray(3)
        val pairTmp = DoubleArray(2)
        val sdx = sd[0]
        val sdy = sd[1]
        val sdz = sd[2]

        for (i in 0 until LW) {
            val az = ((i + 0.5) / LW - 0.5) * 3.2
            atmosInto(sin(az) * cosEc, sinEc, cos(az) * cosEc, sdx, sdy, sdz, cTmp, pairTmp)
            val o = (j * LW + i) * 4

            val lum = cTmp[0] * 0.3 + cTmp[1] * 0.59 + cTmp[2] * 0.11
            val w = 0.8 * expHorizon
            for (k in 0 until 3) {
                cTmp[k] = cTmp[k] * (1.0 - w) + lum * (if (k == 2) 1.08 else 1.0) * w
            }
            for (k in 0 until 3) {
                val nb = nightBias[k]
                val byteVal = (255.0 * clamp(sqrt(max(0.0, (cTmp[k] + nb) * ex) / 4.0), 0.0, 1.0))
                    .toInt().coerceIn(0, 255)
                out[o + k] = byteVal.toByte()
            }
            out[o + 3] = 255.toByte()
        }
    }

    fun moonPhase(nowMs: Long): Double {
        val raw = (nowMs / 86_400_000.0 + 2_440_587.5 - 2_451_550.1) / 29.530588853
        return ((raw % 1.0) + 1.0) % 1.0
    }

    fun frameParams(s: SeaState, nowMs: Long): FrameParams {
        val camE = s.camEff
        val camAz = (camE - 180.0) * D
        val sp = solar(s.t, s.lat, s.doy)
        val sun = camVec(sp.el, sp.az, camAz)
        val elDeg = sp.el / D
        val ex = 0.9 * 10.0.pow(clamp(-elDeg, 0.0, 10.0) * 0.2)
        val tr = trans(sun)
        val over = clamp(s.cloud * 0.8 + s.rain * 0.2 + s.fog * 0.2, 0.0, 1.0)
        val ph = moonPhase(nowMs)
        val ha = (s.t - 12.0) * 15.0 * D - ph * 2.0 * PI
        val la = s.lat * D
        val dm = 5.0 * D
        val sE = sin(la) * sin(dm) + cos(la) * cos(dm) * cos(ha)
        val mEl = asin(clamp(sE, -1.0, 1.0))
        val mAz = atan2(sin(ha), cos(ha) * sin(la) - tan(dm) * cos(la))
        val moon = camVec(mEl, mAz, camAz)
        val pole = camVec(la, PI, camAz)

        val sunL = DoubleArray(3) { k -> tr[k] * 3.2 * ex * (1.0 - 0.88 * over) }
        val sunD = DoubleArray(3) { k -> tr[k] * 60.0 * ex }
        val night = clamp((-elDeg - 3.0) / 12.0, 0.0, 1.0)
        val bow = clamp((s.rain - 0.03) * 12.0, 0.0, 1.0) *
            (1.0 - clamp((s.rain - 0.45) * 5.0, 0.0, 1.0)) *
            clamp((elDeg - 2.0) / 4.0, 0.0, 1.0) *
            (1.0 - clamp((elDeg - 30.0) / 12.0, 0.0, 1.0)) *
            (1.0 - clamp((s.cloud - 0.6) * 3.0, 0.0, 1.0))

        val lh = (((268.0 - camE + 540.0) % 360.0 + 360.0) % 360.0 - 180.0) * D
        val lhFixedB = (((138.0 - camE - 90.0 + 540.0) % 360.0 + 360.0) % 360.0 - 180.0) * D
        val ship = s.shipRel ?: ((((nowMs / 1000.0 * 0.25) % 360.0 - camE + 540.0) % 360.0 + 360.0) % 360.0 - 180.0) * D

        return FrameParams(
            bow = bow,
            lh = lh,
            lhMode = s.lhMode,
            lhFixedB = lhFixedB,
            ship = ship,
            night2 = clamp((-elDeg - 1.0) / 6.0, 0.0, 1.0),
            obj = floatArrayOf(
                if (s.lhOn) 1f else 0f,
                if (s.shipOn) 1f else 0f,
                if (s.birdsOn) 1f else 0f,
            ),
            bolt = s.bolt,
            sun = sun,
            sunL = sunL,
            sunD = sunD,
            moon = moon,
            moonPh = ph,
            moonL = ex * 0.012 * (0.2 + 0.8 * night),
            exp = ex,
            pole = pole,
            starRot = (s.t * 15.0 + s.doy * 0.9856) * D,
            star = night * (1.0 - clamp(s.cloud * 1.3, 0.0, 1.0)),
            elDeg = elDeg,
            over = over,
            wdir = (90.0 - (s.windDir - camE)) * D,
            cloud = s.cloud,
            wind = s.wind,
            rain = s.rain,
            fog = s.fog,
            flash = s.flash,
        )
    }

    fun skyAmbient(lut: ByteArray): DoubleArray {
        val o = ((LH - 1) * LW + LW / 2) * 4
        return DoubleArray(3) { k ->
            val x = (lut[o + k].toInt() and 0xFF) / 255.0
            x * x * 4.0
        }
    }

    fun makeLight(p: FrameParams, lut: ByteArray): LightModel {
        val amb = p.amb
        val avg = (amb[0] + amb[1] + amb[2]) / 3.0
        val d = clamp((avg - 0.004) / 0.056, 0.0, 1.0)
        val o = (2 * 64 + 32) * 4
        val hz = DoubleArray(3) { k ->
            val x = (lut[o + k].toInt() and 0xFF) / 255.0
            LightModel.disp(x * x * 4.0)
        }
        return LightModel(
            dayF = d * d * (3.0 - 2.0 * d),
            night = p.night2,
            sun = p.sun,
            amb = amb,
            sunL = p.sunL,
            hazeColor = hz,
        )
    }

    fun makeNrm(): ByteArray {
        val n = 256
        val h = FloatArray(n * n)
        var s = 5
        fun rs(): Float {
            s += 0x6D2B79F5
            var t = (s xor (s ushr 15)) * (1 or s)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }

        for (q in 0 until 5) {
            val g = 4 shl q
            val gr = FloatArray(g * g) { rs() }
            val am = 0.55f.pow(q)
            for (y in 0 until n) {
                for (x in 0 until n) {
                    val fx = x.toFloat() / n * g
                    val fy = y.toFloat() / n * g
                    val ix = floor(fx).toInt()
                    val iy = floor(fy).toInt()
                    val tx = fx - ix
                    val ty = fy - iy
                    val sx = tx * tx * (3f - 2f * tx)
                    val sy = ty * ty * (3f - 2f * ty)
                    val x1 = (ix + 1) % g
                    val y1 = (iy + 1) % g
                    val a = gr[iy * g + ix]
                    val b = gr[iy * g + x1]
                    val cc = gr[y1 * g + ix]
                    val d = gr[y1 * g + x1]
                    h[y * n + x] += am * (a + (b - a) * sx + (cc - a) * sy + (a - b - cc + d) * sx * sy)
                }
            }
        }
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in h) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val span = mx - mn
        for (i in h.indices) h[i] = (h[i] - mn) / span

        val gx = FloatArray(n * n)
        val gy = FloatArray(n * n)
        var m = 1e-6f
        for (y in 0 until n) {
            for (x in 0 until n) {
                val i = y * n + x
                gx[i] = h[y * n + ((x + 1) and 255)] - h[y * n + ((x + 255) and 255)]
                gy[i] = h[((y + 1) and 255) * n + x] - h[((y + 255) and 255) * n + x]
                m = max(m, max(abs(gx[i]), abs(gy[i])))
            }
        }
        val out = ByteArray(n * n * 4)
        for (i in 0 until n * n) {
            out[i * 4] = (128f + 127f * clamp((gx[i] / (m * 0.5f)).toDouble(), -1.0, 1.0).toFloat()).toInt().coerceIn(0, 255).toByte()
            out[i * 4 + 1] = (128f + 127f * clamp((gy[i] / (m * 0.5f)).toDouble(), -1.0, 1.0).toFloat()).toInt().coerceIn(0, 255).toByte()
            out[i * 4 + 2] = (h[i] * 255f).toInt().coerceIn(0, 255).toByte()
            val idxA = ((((i shr 8) + 97) and 255) shl 8) or ((i + 31) and 255)
            out[i * 4 + 3] = (h[idxA] * 255f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    private fun hypot3(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)
}

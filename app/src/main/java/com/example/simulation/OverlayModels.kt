package com.example.simulation

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object V3 {
    fun add(a: DoubleArray, b: DoubleArray): DoubleArray =
        doubleArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])

    fun sub(a: DoubleArray, b: DoubleArray): DoubleArray =
        doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

    fun mul(a: DoubleArray, k: Double): DoubleArray =
        doubleArrayOf(a[0] * k, a[1] * k, a[2] * k)

    fun dot(a: DoubleArray, b: DoubleArray): Double =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun cross(a: DoubleArray, b: DoubleArray): DoubleArray =
        doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )

    fun norm(a: DoubleArray): DoubleArray {
        val m = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).let { if (it == 0.0) 1.0 else it }
        return doubleArrayOf(a[0] / m, a[1] / m, a[2] / m)
    }

    fun lerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray =
        doubleArrayOf(
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t,
        )
}

fun wrapRad(angle: Double): Double {
    var a = angle
    while (a > PI) a -= 2.0 * PI
    while (a < -PI) a += 2.0 * PI
    return a
}

enum class GullMode { FLAP, GLIDE, SOAR }

class GullState(
    var x: Double,
    var y: Double,
    var z: Double,
    var psi: Double,
    var th: Double,
    var ph: Double,
    var v: Double,
    var fl: Double,
    var a: Double,
    var mode: GullMode,
    var mt: Double,
    var tx: Double,
    var ty: Double,
    var tz: Double,
    var sc: Double,
    var turn: Double,
    var circ: Double,
) {
    fun rotWorld(d: Double) {
        val c = cos(d)
        val s = sin(d)
        val nx = x * c - z * s
        val nz = x * s + z * c
        x = nx
        z = nz
        val ntx = tx * c - tz * s
        val ntz = tx * s + tz * c
        tx = ntx
        tz = ntz
        psi = wrapRad(psi - d)
    }

    fun step(dt: Double) {
        mt -= dt
        if (mt <= 0.0) {
            if (mode == GullMode.FLAP) {
                mode = if (Random.nextDouble() < 0.62) GullMode.GLIDE else GullMode.SOAR
                mt = 3.5 + Random.nextDouble() * 4.5
                circ = if (Random.nextDouble() < 0.5) 1.0 else -1.0
            } else {
                mode = GullMode.FLAP
                mt = 2.2 + Random.nextDouble() * 2.8
            }
        }
        val dx = tx - x
        val dz = tz - z
        if (hypot(dx, dz) < 14.0 || z < 15.0) pickTarget()
        var da = wrapRad(atan2(dx, dz) - psi)
        if (mode == GullMode.SOAR) {
            da = 0.28 * circ
        }
        val targetTurn = max(-0.46, min(0.46, da * 0.72))
        turn += (targetTurn - turn) * min(1.0, dt * 1.15)
        psi = wrapRad(psi + turn * dt)

        val downstrokeSurge = if (mode == GullMode.FLAP) 0.4 * a * max(0.0, -sin(fl)) else 0.0
        val vt = when (mode) {
            GullMode.FLAP -> 8.6 + downstrokeSurge
            GullMode.GLIDE -> 7.8
            GullMode.SOAR -> 6.8
        }
        v += (vt - v) * min(1.0, dt * 0.75)

        val climbBias = when (mode) {
            GullMode.SOAR -> 0.22
            GullMode.FLAP -> 0.24
            GullMode.GLIDE -> -0.32
        }
        val climb = max(-2.2, min(2.2, (ty - y) * 0.09)) + climbBias
        val targetTh = max(-0.24, min(0.24, climb / max(1.0, v)))
        th += (targetTh - th) * min(1.0, dt * 1.05)

        val cosTh = cos(th)
        x += sin(psi) * v * dt * cosTh
        z += cos(psi) * v * dt * cosTh
        y = max(-3.2, y + sin(th) * v * dt)

        val targetRoll = max(-0.68, min(0.68, turn * 1.38))
        ph += (targetRoll - ph) * min(1.0, dt * 1.85)

        val targetA = if (mode == GullMode.FLAP) 1.0 else 0.0
        a += (targetA - a) * min(1.0, dt * 1.85)

        val baseFreq = 1.92 + 0.22 * sin(sc * 9.0) + 0.14 * max(0.0, th)
        if (a > 0.03) {
            // Keep advancing smoothly while amplitude fades so wings never freeze mid-stroke
            val activeFreq = baseFreq * (0.35 + 0.65 * a)
            fl += activeFreq * 6.2831853 * dt
        } else {
            // Gently settle phase toward neutral glide arch
            fl += wrapRad(0.2 - wrapRad(fl)) * min(1.0, dt * 1.5)
        }
    }

    fun pickTarget() {
        val zt = 24.0 + Random.nextDouble() * 64.0
        tz = zt
        tx = (Random.nextDouble() * 2.0 - 1.0) * zt * 0.78
        ty = -1.2 + Random.nextDouble() * (0.24 * zt + 2.2)
    }

    companion object {
        fun spawn(): GullState {
            val z = 24.0 + Random.nextDouble() * 52.0
            return GullState(
                x = (Random.nextDouble() * 2.0 - 1.0) * z * 0.7,
                y = -1.2 + Random.nextDouble() * (0.24 * z + 2.0),
                z = z,
                psi = Random.nextDouble() * 6.28,
                th = 0.0,
                ph = 0.0,
                v = 7.8,
                fl = Random.nextDouble() * 6.28,
                a = 1.0,
                mode = if (Random.nextDouble() < 0.55) GullMode.FLAP else GullMode.GLIDE,
                mt = 1.5 + Random.nextDouble() * 3.5,
                tx = 0.0,
                ty = 0.0,
                tz = 0.0,
                sc = 0.88 + Random.nextDouble() * 0.28,
                turn = 0.0,
                circ = if (Random.nextDouble() < 0.5) 1.0 else -1.0,
            ).apply { pickTarget() }
        }
    }
}

data class ShipEnv(
    val lee: Double,
    val boom: Double,
    val heel: Double,
    val wave: Double,
)

class ShipState(
    var x: Double,
    var z: Double,
    var psi: Double,
    var v: Double = 2.6,
    var fast: Boolean = false,
    var t: Double = 0.0,
) {
    fun rotWorld(d: Double) {
        val c = cos(d)
        val s = sin(d)
        val nx = x * c - z * s
        val nz = x * s + z * c
        x = nx
        z = nz
        psi -= d
    }

    fun step(dt: Double) {
        val speed = v * (if (fast) 4.0 else 1.0)
        x += sin(psi) * speed * dt
        z += cos(psi) * speed * dt
        t += dt
    }

    fun env(windDir: Double, wind: Double, camE: Double): ShipEnv {
        val ww = (windDir - camE) * PI / 180.0
        val wvx = sin(ww)
        val wvz = cos(ww)
        val hvx = sin(psi)
        val hvz = cos(psi)
        val cosg = max(-1.0, min(1.0, -(wvx * hvx + wvz * hvz)))
        val beta = acos(cosg)
        val sd = if ((hvx * -wvz - hvz * -wvx) >= 0.0) 1.0 else -1.0
        return ShipEnv(
            lee = -sd,
            boom = max(0.12, min(1.4, beta * 0.5)),
            heel = -sd * (0.04 + 0.2 * wind) * min(1.0, sin(beta) * 1.4 + 0.1),
            wave = 0.3 + wind * 1.2,
        )
    }

    companion object {
        val HULL_F = doubleArrayOf(-5.0, -4.2, -3.0, -1.5, 0.0, 1.5, 3.0, 4.2, 5.0, 5.6)
        val HULL_W = doubleArrayOf(0.95, 1.25, 1.55, 1.75, 1.8, 1.65, 1.3, 0.85, 0.4, 0.0)
        val HULL_H = doubleArrayOf(0.85, 0.82, 0.8, 0.8, 0.82, 0.9, 1.0, 1.1, 1.18, 1.26)
        val HULL_WL = doubleArrayOf(0.55, 0.8, 1.1, 1.25, 1.25, 1.05, 0.7, 0.3, 0.08, 0.0)

        fun spawn(near: Boolean, fast: Boolean): ShipState {
            val side = if (Random.nextDouble() < 0.5) -1.0 else 1.0
            val x0: Double
            val z0: Double
            val xt: Double
            val zt: Double
            if (near) {
                x0 = -170.0
                z0 = 300.0
                xt = 40.0
                zt = 110.0
            } else {
                x0 = side * (300.0 + Random.nextDouble() * 150.0)
                z0 = 420.0 + Random.nextDouble() * 220.0
                xt = -side * (20.0 + Random.nextDouble() * 60.0)
                zt = 110.0 + Random.nextDouble() * 90.0
            }
            return ShipState(x = x0, z = z0, psi = atan2(xt - x0, zt - z0), fast = fast)
        }
    }
}

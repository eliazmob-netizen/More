package com.example.simulation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class OverlayCanvasRenderer {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val roundStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val path = Path()

    data class ViewTransform(
        val tx: Double,
        val ty: Double,
        val pit: Double,
        val rl: Double,
    )

    private class Basis(val f: DoubleArray, val r: DoubleArray, val u: DoubleArray)

    private fun basis(psi: Double, th: Double, ph: Double): Basis {
        val f = doubleArrayOf(sin(psi) * cos(th), sin(th), cos(psi) * cos(th))
        val r0 = doubleArrayOf(cos(psi), 0.0, -sin(psi))
        val u0 = V3.cross(f, r0)
        val r = V3.add(V3.mul(r0, cos(ph)), V3.mul(u0, -sin(ph)))
        val u = V3.add(V3.mul(u0, cos(ph)), V3.mul(r0, sin(ph)))
        return Basis(f, r, u)
    }

    private fun argb(c: DoubleArray, alpha: Double = 1.0): Int {
        val a = (alpha * 255.0).toInt().coerceIn(0, 255)
        val r = (c[0] * 255.0).toInt().coerceIn(0, 255)
        val g = (c[1] * 255.0).toInt().coerceIn(0, 255)
        val b = (c[2] * 255.0).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun ensureSize(width: Int, height: Int): Bitmap {
        val existing = bitmap
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        existing?.recycle()
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap = created
        canvas = Canvas(created)
        return created
    }

    fun render(
        width: Int,
        height: Int,
        view: ViewTransform,
        light: LightModel,
        ship: ShipState?,
        shipOn: Boolean,
        windDir: Double,
        wind: Double,
        camEff: Double,
        birds: List<GullState>,
        birdsOn: Boolean,
    ): Bitmap {
        val bmp = ensureSize(width, height)
        val g = canvas!!
        g.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val wF = width.toDouble()
        val hF = height.toDouble()
        val cRl = cos(view.rl)
        val sRl = sin(view.rl)
        val tanPit = tan(view.pit) * 1.15

        fun proj(x: Double, y: Double, z: Double): DoubleArray {
            val u = 1.15 * x / z - view.tx * 0.12
            val v = 1.15 * y / z + 0.10 - view.ty * 0.12 - tanPit
            return doubleArrayOf(
                wF * 0.5 + (u * cRl + v * sRl) * hF,
                hF * 0.5 - (-u * sRl + v * cRl) * hF,
                1.15 / z * hF,
            )
        }

        if (ship != null && shipOn && ship.z > 14.0) {
            val env = ship.env(windDir, wind, camEff)
            val yw = proj(ship.x, -4.5, ship.z)[1]
            drawShip(g, ::proj, ship, light, env, yw)
            drawShip(g, ::proj, ship, light, env, null)
        }

        if (birdsOn) {
            val sorted = birds.sortedByDescending { it.z }
            for (b in sorted) {
                if (b.z > 6.0) {
                    drawGull(g, ::proj, b, light)
                }
            }
        }
        return bmp
    }

    private data class WingGeom(
        val pts: List<DoubleArray>,
        val n: DoubleArray,
        val depth: Double,
    )

    private fun drawGull(
        g: Canvas,
        proj: (Double, Double, Double) -> DoubleArray,
        b: GullState,
        l: LightModel,
    ) {
        val dayA = l.dayF
        if (dayA < 0.02) return

        val bBasis = basis(b.psi, b.th, b.ph)
        val sc = b.sc
        val pOrigin = doubleArrayOf(b.x, b.y, b.z)
        val amp = b.a
        val s = sin(b.fl)
        val hz = minOf(0.5, b.z / 380.0)
        val sun = l.sun
        val alphaByte = (dayA * (1.0 - hz * 0.3) * 255.0).toInt().coerceIn(0, 255)

        fun pt(f: Double, u: Double, r: Double): DoubleArray =
            V3.add(
                pOrigin,
                V3.add(
                    V3.add(V3.mul(bBasis.f, f * sc), V3.mul(bBasis.u, u * sc)),
                    V3.mul(bBasis.r, r * sc),
                ),
            )

        val bob = -0.035 * amp * s

        fun wing(side: Double): WingGeom {
            val al = (1.0 - amp) * 0.26 + amp * (0.14 + 0.78 * s)
            val ah = (1.0 - amp) * -0.1 + amp * (0.12 + 1.0 * sin(b.fl - 0.9))
            val sw1 = 0.28 + 0.1 * (1.0 - amp)
            val sw2 = 0.55 - 0.25 * amp * max(0.0, s)
            val lh = 0.27 * (1.0 - 0.2 * amp * max(0.0, -s))
            val d1 = doubleArrayOf(-sin(sw1) * cos(al), sin(al), side * cos(sw1) * cos(al))
            val d2 = doubleArrayOf(-sin(sw2) * cos(ah), sin(ah), side * cos(sw2) * cos(ah))
            val s0 = doubleArrayOf(0.06, 0.03 + bob, side * 0.05)
            val w0 = V3.add(s0, V3.mul(d1, 0.38))
            val t0 = V3.add(w0, V3.mul(d2, lh))
            val sb = doubleArrayOf(s0[0] - 0.25, s0[1] - 0.012 - 0.04 * amp * s, s0[2])
            val wb = doubleArrayOf(w0[0] - 0.17, w0[1] - 0.02 - 0.05 * amp * s, w0[2])
            val tb = doubleArrayOf(t0[0] - 0.055, t0[1] - 0.01, t0[2])
            val mw = V3.lerp(s0, w0, 0.5)
            mw[1] += 0.012
            val nl = V3.norm(doubleArrayOf(0.0, side * d1[2], -side * d1[1]))
            val nW = V3.add(V3.add(V3.mul(bBasis.u, nl[1]), V3.mul(bBasis.r, nl[2])), V3.mul(bBasis.f, nl[0]))
            val pts = listOf(s0, mw, w0, t0, tb, wb, sb).map { pt(it[0], it[1], it[2]) }
            val c = V3.lerp(pts[0], pts[3], 0.5)
            return WingGeom(pts, nW, c[2])
        }

        val wr = wing(1.0)
        val wl = wing(-1.0)

        fun drawWing(w: WingGeom) {
            val sp = w.pts.map { proj(it[0], it[1], it[2]) }
            val top = V3.dot(w.n, V3.sub(doubleArrayOf(0.0, 0.0, 0.0), w.pts[2])) > 0.0
            val lam = max(0.0, V3.dot(if (top) w.n else V3.mul(w.n, -1.0), sun))
            val base = if (top) {
                l.shade(doubleArrayOf(0.52, 0.57, 0.63), 0.35 + 0.65 * lam)
            } else {
                l.shade(doubleArrayOf(0.95, 0.96, 0.97), 0.45 + 0.4 * max(0.0, lam))
            }
            val dark = l.shade(doubleArrayOf(0.05, 0.05, 0.06), 0.5)
            val a = sp[0]
            val t = sp[3]
            val baseColor = argb(l.haze(base, hz), alphaByte / 255.0)
            val darkColor = argb(l.haze(dark, hz * 0.5), alphaByte / 255.0)
            val dx = t[0] - a[0]
            val dy = t[1] - a[1]
            if (dx * dx + dy * dy > 1e-4) {
                fillPaint.shader = LinearGradient(
                    a[0].toFloat(),
                    a[1].toFloat(),
                    t[0].toFloat(),
                    t[1].toFloat(),
                    intArrayOf(baseColor, baseColor, darkColor, darkColor),
                    floatArrayOf(0f, 0.62f, 0.8f, 1f),
                    Shader.TileMode.CLAMP,
                )
            } else {
                fillPaint.shader = null
                fillPaint.color = baseColor
            }
            path.reset()
            path.moveTo(sp[0][0].toFloat(), sp[0][1].toFloat())
            for (i in 1 until sp.size) {
                path.lineTo(sp[i][0].toFloat(), sp[i][1].toFloat())
            }
            path.close()
            g.drawPath(path, fillPaint)
            fillPaint.shader = null

            strokePaint.color = Color.argb((71 * (alphaByte / 255f)).toInt().coerceIn(0, 255), 50, 58, 70)
            strokePaint.strokeWidth = max(0.5, sp[0][2] * 0.012).toFloat()
            g.drawPath(path, strokePaint)
        }

        val first = if (wr.depth > wl.depth) wr else wl
        val second = if (first === wr) wl else wr
        drawWing(first)

        val tailPts = listOf(
            pt(-0.26, bob, -0.025),
            pt(-0.43, bob - 0.01, -0.085),
            pt(-0.45, bob - 0.01, 0.085),
            pt(-0.26, bob, 0.025),
        ).map { proj(it[0], it[1], it[2]) }
        path.reset()
        tailPts.forEachIndexed { idx, p ->
            if (idx == 0) path.moveTo(p[0].toFloat(), p[1].toFloat())
            else path.lineTo(p[0].toFloat(), p[1].toFloat())
        }
        path.close()
        fillPaint.shader = null
        fillPaint.color = argb(l.haze(l.shade(doubleArrayOf(0.96, 0.96, 0.97), 0.6), hz), alphaByte / 255.0)
        g.drawPath(path, fillPaint)

        val k = proj(pOrigin[0], pOrigin[1], pOrigin[2])[2]
        val bt = proj(pt(-0.26, bob, 0.0)[0], pt(-0.26, bob, 0.0)[1], pt(-0.26, bob, 0.0)[2])
        val pBc = pt(0.17, bob + 0.01, 0.0)
        val bc = proj(pBc[0], pBc[1], pBc[2])
        val pHd = pt(0.25, bob + 0.03, 0.0)
        val hd = proj(pHd[0], pHd[1], pHd[2])
        val pBl = pt(0.33, bob + 0.025, 0.0)
        val bl = proj(pBl[0], pBl[1], pBl[2])

        val white = argb(l.haze(l.shade(doubleArrayOf(0.97, 0.97, 0.98), 0.65 + 0.3 * max(0.0, sun[1])), hz), alphaByte / 255.0)
        roundStrokePaint.color = white
        roundStrokePaint.strokeWidth = max(1.0, 0.16 * k * sc).toFloat()
        g.drawLine(bt[0].toFloat(), bt[1].toFloat(), bc[0].toFloat(), bc[1].toFloat(), roundStrokePaint)

        roundStrokePaint.strokeWidth = max(1.0, 0.085 * k * sc).toFloat()
        g.drawLine(bc[0].toFloat(), bc[1].toFloat(), hd[0].toFloat(), hd[1].toFloat(), roundStrokePaint)

        roundStrokePaint.color = argb(l.haze(l.shade(doubleArrayOf(0.95, 0.65, 0.15), 0.8), hz), alphaByte / 255.0)
        roundStrokePaint.strokeWidth = max(0.8, 0.028 * k * sc).toFloat()
        g.drawLine(hd[0].toFloat(), hd[1].toFloat(), bl[0].toFloat(), bl[1].toFloat(), roundStrokePaint)

        drawWing(second)
    }

    private fun drawShip(
        g: Canvas,
        proj: (Double, Double, Double) -> DoubleArray,
        s: ShipState,
        l: LightModel,
        env: ShipEnv,
        mirrorY: Double?,
    ) {
        val camH = 4.5
        val pit = 0.018 * sin(s.t * 0.7)
        val bBasis = basis(s.psi, pit, env.heel)
        val o = doubleArrayOf(s.x, -camH + 0.12 * sin(s.t * 0.9) * env.wave, s.z)
        val hazeK = minOf(0.55, s.z / 1500.0)
        val alphaMul = if (mirrorY != null) 0.3 else 1.0

        fun pt(f: Double, u: Double, r: Double): DoubleArray =
            V3.add(o, V3.add(V3.add(V3.mul(bBasis.f, f), V3.mul(bBasis.u, u)), V3.mul(bBasis.r, r)))

        fun pProj(w: DoubleArray): DoubleArray {
            val q = proj(w[0], w[1], w[2])
            return if (mirrorY != null) {
                doubleArrayOf(
                    q[0] + sin(q[1] * 0.45 + s.t * 2.2) * env.wave * q[2] * 0.05,
                    2.0 * mirrorY - q[1],
                    q[2],
                )
            } else {
                q
            }
        }

        val k = pProj(o)[2]
        val sun = l.sun
        val toCam = V3.sub(doubleArrayOf(0.0, 0.0, 0.0), o)

        fun poly(pts: List<DoubleArray>, fillColor: Int?, lineColor: Int?, lw: Double? = null) {
            path.reset()
            pts.forEachIndexed { i, p ->
                val q = pProj(p)
                if (i == 0) path.moveTo(q[0].toFloat(), q[1].toFloat())
                else path.lineTo(q[0].toFloat(), q[1].toFloat())
            }
            path.close()
            if (fillColor != null) {
                fillPaint.shader = null
                fillPaint.color = fillColor
                g.drawPath(path, fillPaint)
            }
            if (lineColor != null) {
                strokePaint.color = lineColor
                strokePaint.strokeWidth = (lw ?: max(0.5, k * 0.03)).toFloat()
                g.drawPath(path, strokePaint)
            }
        }

        fun line(a: DoubleArray, b: DoubleArray, w: Double, color: Int) {
            val p = pProj(a)
            val q = pProj(b)
            strokePaint.color = color
            strokePaint.strokeWidth = max(0.6, w * k).toFloat()
            g.drawLine(p[0].toFloat(), p[1].toFloat(), q[0].toFloat(), q[1].toFloat(), strokePaint)
        }

        fun col(a: DoubleArray, lam: Double): Int =
            argb(l.haze(l.shade(a, lam), hazeK), alphaMul)

        fun lineCol(r: Int, gC: Int, bC: Int, aBase: Double): Int =
            Color.argb((aBase * alphaMul * 255.0).toInt().coerceIn(0, 255), r, gC, bC)

        fun sideN(sd: Double): DoubleArray = V3.mul(bBasis.r, sd)
        val near = if (V3.dot(bBasis.r, toCam) > 0.0) 1.0 else -1.0

        // Wake trail behind stern
        if (mirrorY == null) {
            for (i in 0 until 14) {
                val f0 = -5.0 - i * 4.0
                val f1 = -5.0 - (i + 1) * 4.0
                val w0 = 0.5 + i * 0.28
                val w1 = 0.5 + (i + 1) * 0.28
                path.reset()
                val coords = arrayOf(
                    doubleArrayOf(f0, -w0),
                    doubleArrayOf(f1, -w1),
                    doubleArrayOf(f1, w1),
                    doubleArrayOf(f0, w0),
                )
                coords.forEachIndexed { j, c ->
                    val w = V3.add(o, V3.add(V3.mul(bBasis.f, c[0]), V3.mul(bBasis.r, c[1])))
                    val q = pProj(w)
                    if (j == 0) path.moveTo(q[0].toFloat(), q[1].toFloat())
                    else path.lineTo(q[0].toFloat(), q[1].toFloat())
                }
                path.close()
                val wakeAlpha = (0.22 * (1.0 - i / 14.0) * l.dayF + 0.05 * (1.0 - i / 14.0))
                fillPaint.shader = null
                fillPaint.color = Color.argb((wakeAlpha * 255.0).toInt().coerceIn(0, 255), 255, 255, 255)
                g.drawPath(path, fillPaint)
            }
        }

        // Hull sides: far -> near
        fun hullSide(sd: Double) {
            val top = ShipState.HULL_F.indices.map { i -> pt(ShipState.HULL_F[i], ShipState.HULL_H[i], sd * ShipState.HULL_W[i]) }
            val bot = ShipState.HULL_F.indices.reversed().map { i -> pt(ShipState.HULL_F[i], 0.0, sd * ShipState.HULL_WL[i]) }
            val lam = max(0.0, V3.dot(sideN(sd), sun))
            poly(top + bot, col(doubleArrayOf(0.93, 0.94, 0.95), 0.4 + 0.6 * lam), lineCol(30, 40, 55, 0.35))

            val st1 = ShipState.HULL_F.indices.map { i -> pt(ShipState.HULL_F[i], ShipState.HULL_H[i] - 0.14, sd * (ShipState.HULL_W[i] - 0.005)) }
            val st2 = ShipState.HULL_F.indices.reversed().map { i -> pt(ShipState.HULL_F[i], ShipState.HULL_H[i] - 0.3, sd * (ShipState.HULL_W[i] - 0.07)) }
            poly(st1 + st2, col(doubleArrayOf(0.08, 0.13, 0.28), 0.5 + 0.5 * lam), null)

            val af1 = ShipState.HULL_F.indices.map { i -> pt(ShipState.HULL_F[i], 0.0, sd * ShipState.HULL_WL[i]) }
            val af2 = ShipState.HULL_F.indices.reversed().map { i -> pt(ShipState.HULL_F[i], 0.24, sd * (ShipState.HULL_WL[i] + 0.1)) }
            poly(af1 + af2, col(doubleArrayOf(0.42, 0.09, 0.09), 0.4 + 0.5 * lam), null)
        }
        hullSide(-near)
        hullSide(near)

        // Deck & transom
        val deckLeft = ShipState.HULL_F.indices.map { i -> pt(ShipState.HULL_F[i], ShipState.HULL_H[i], -ShipState.HULL_W[i]) }
        val deckRight = ShipState.HULL_F.indices.reversed().map { i -> pt(ShipState.HULL_F[i], ShipState.HULL_H[i], ShipState.HULL_W[i]) }
        poly(deckLeft + deckRight, col(doubleArrayOf(0.72, 0.62, 0.45), 0.5 + 0.4 * max(0.0, sun[1])), null)

        if (V3.dot(bBasis.f, toCam) < 0.0) {
            poly(
                listOf(pt(-5.0, 0.85, -0.95), pt(-5.0, 0.85, 0.95), pt(-5.0, 0.0, 0.55), pt(-5.0, 0.0, -0.55)),
                col(doubleArrayOf(0.9, 0.91, 0.93), 0.45),
                null,
            )
        }

        // Cabin
        fun cab(sd: Double) {
            val lam = max(0.0, V3.dot(sideN(sd), sun))
            poly(
                listOf(pt(-2.0, 0.82, sd * 0.62), pt(-1.9, 1.35, sd * 0.5), pt(1.3, 1.4, sd * 0.5), pt(2.2, 0.84, sd * 0.62)),
                col(doubleArrayOf(0.95, 0.95, 0.96), 0.45 + 0.55 * lam),
                lineCol(30, 40, 55, 0.3),
            )
            if (sd == near) {
                for (i in 0 until 3) {
                    val f0 = -1.4 + i * 1.1
                    poly(
                        listOf(pt(f0, 1.0, sd * 0.58), pt(f0, 1.28, sd * 0.52), pt(f0 + 0.7, 1.3, sd * 0.52), pt(f0 + 0.7, 1.02, sd * 0.58)),
                        col(doubleArrayOf(0.1, 0.14, 0.2), 0.6),
                        null,
                    )
                }
            }
        }
        cab(-near)
        poly(
            listOf(pt(-1.9, 1.35, -0.5), pt(-1.9, 1.35, 0.5), pt(1.3, 1.4, 0.5), pt(1.3, 1.4, -0.5)),
            col(doubleArrayOf(0.95, 0.95, 0.96), 0.55 + 0.3 * max(0.0, sun[1])),
            null,
        )
        cab(near)

        // Mast, boom, sails, rigging
        val mt = pt(1.4, 12.2, 0.0)
        val mb = pt(1.4, 1.4, 0.0)
        val bst = pt(-5.0, 1.0, 0.0)
        val bow = pt(5.4, 1.1, 0.0)
        val lee = env.lee
        val ba = env.boom
        val boomEnd = pt(1.4 - 5.1 * cos(ba), 1.6, lee * 5.1 * sin(ba))
        val tack = pt(1.4, 1.75, 0.0)

        fun sailCol(a: DoubleArray, b: DoubleArray, c: DoubleArray): Int {
            val n = V3.norm(V3.cross(V3.sub(b, a), V3.sub(c, a)))
            val face = if (V3.dot(n, V3.sub(doubleArrayOf(0.0, 0.0, 0.0), a)) > 0.0) n else V3.mul(n, -1.0)
            val lam = V3.dot(face, sun)
            val back = max(0.0, -V3.dot(face, sun))
            return argb(
                l.haze(l.shade(doubleArrayOf(0.95, 0.92, 0.84), 0.5 + 0.5 * max(0.0, lam) + 0.45 * back * l.dayF), hazeK),
                alphaMul,
            )
        }

        fun curve(a: DoubleArray, b: DoubleArray, c: DoubleArray, n: Int): List<DoubleArray> {
            return List(n + 1) { i ->
                val t = i.toDouble() / n
                V3.add(V3.add(V3.mul(a, (1.0 - t) * (1.0 - t)), V3.mul(b, 2.0 * (1.0 - t) * t)), V3.mul(c, t * t))
            }
        }

        fun sail(a: DoubleArray, hd: DoubleArray, cl: DoubleArray, rb: Double, dr: Double) {
            val mid = V3.add(
                V3.lerp(hd, cl, 0.5),
                V3.mul(V3.norm(V3.sub(V3.lerp(hd, cl, 0.5), V3.lerp(a, hd, 0.5))), rb),
            )
            mid[2] += lee * dr
            val leech = curve(hd, mid, cl, 8)
            poly(listOf(a, hd) + leech.drop(1), sailCol(a, hd, cl), lineCol(60, 55, 45, 0.3))
            for (i in 1 until 4) {
                val t = i / 4.0
                val l0 = V3.lerp(a, hd, t * 0.92 + 0.04)
                val l1 = leech[((1.0 - t) * 8.0).roundToInt().coerceIn(0, 8)]
                line(l0, l1, 0.012, lineCol(90, 85, 70, 0.35))
            }
        }

        val jib = arrayOf(
            pt(5.0, 1.3, 0.0),
            pt(1.7, 10.6, 0.0),
            pt(0.6, 1.6, lee * (0.8 + 1.6 * sin(ba))),
        )
        sail(jib[0], jib[1], jib[2], 0.4, 0.0)
        line(mb, mt, 0.11, col(doubleArrayOf(0.75, 0.77, 0.8), 0.5))
        line(tack, boomEnd, 0.07, col(doubleArrayOf(0.75, 0.77, 0.8), 0.5))
        sail(tack, pt(1.4, 12.0, 0.0), boomEnd, 1.0, 0.5)

        val rigging = listOf(
            mt to bow,
            mt to bst,
            pt(1.4, 7.0, 0.0) to pt(1.0, 0.85, 1.75),
            pt(1.4, 7.0, 0.0) to pt(1.0, 0.85, -1.75),
            mt to pt(1.1, 0.85, 1.7),
            mt to pt(1.1, 0.85, -1.7),
        )
        rigging.forEach { (a, b) -> line(a, b, 0.015, lineCol(40, 40, 45, 0.55)) }
        line(pt(1.4, 7.0, -0.9), pt(1.4, 7.0, 0.9), 0.02, lineCol(60, 60, 66, 0.8))

        // Night navigation lights
        if (l.night > 0.05) {
            val cd = V3.norm(toCam)
            val fw = V3.dot(bBasis.f, cd)
            val sd = V3.dot(bBasis.r, cd)
            val side = abs(sd) < 0.18
            val lights = listOf(
                Triple(pt(4.7, 1.2, 0.35), doubleArrayOf(0.1, 1.0, 0.35), fw > -0.38 && (sd > 0.0 || side)),
                Triple(pt(4.7, 1.2, -0.35), doubleArrayOf(1.0, 0.15, 0.1), fw > -0.38 && (sd < 0.0 || side)),
                Triple(pt(-4.9, 1.9, 0.0), doubleArrayOf(1.0, 0.95, 0.85), fw < 0.38),
                Triple(mt, doubleArrayOf(1.0, 0.95, 0.85), true),
            )
            for ((pos, cLight, visible) in lights) {
                if (!visible) continue
                val q = pProj(pos)
                val r = max(4.0, k * 0.9).toFloat()
                val c0 = argb(cLight, 0.95 * l.night * alphaMul)
                val c1 = argb(cLight, 0.0)
                addPaint.shader = RadialGradient(
                    q[0].toFloat(),
                    q[1].toFloat(),
                    r,
                    c0,
                    c1,
                    Shader.TileMode.CLAMP,
                )
                g.drawRect(
                    q[0].toFloat() - r,
                    q[1].toFloat() - r,
                    q[0].toFloat() + r,
                    q[1].toFloat() + r,
                    addPaint,
                )
            }
            addPaint.shader = null
        }
    }
}

package com.example.simulation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt

class WeatherHudRenderer {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var lastKey: String? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(74, 8, 16, 30)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(66, 220, 240, 255)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1.5f, Color.argb(175, 0, 8, 18))
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 224, 240, 255)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(3.5f, 0f, 1f, Color.argb(165, 0, 8, 18))
    }

    data class HudRenderResult(
        val bitmap: Bitmap,
        val changed: Boolean,
        val widthPx: Int,
        val heightPx: Int,
    )

    fun renderIfNeeded(
        screenWidth: Int,
        state: SeaState,
        simHours: Double,
        elDeg: Double,
    ): HudRenderResult {
        val density = (screenWidth / 390f).coerceIn(1.2f, 3.6f)
        val lines = formatLines(state, simHours, elDeg)
        val key = "$screenWidth|${lines.first}|${lines.second}"

        val existing = bitmap
        if (existing != null && key == lastKey) {
            return HudRenderResult(existing, changed = false, existing.width, existing.height)
        }

        titlePaint.textSize = 14.5f * density
        subPaint.textSize = 12.0f * density
        borderPaint.strokeWidth = max(1f, 1.0f * density)

        val maxW = (screenWidth * 0.90f).roundToInt().coerceAtLeast(180)
        val padX = 18f * density
        val padY = 10f * density
        val lineGap = 4f * density

        val t1W = titlePaint.measureText(lines.first)
        val t2W = subPaint.measureText(lines.second)
        val contentW = max(t1W, t2W)
        val boxW = (contentW + padX * 2f).roundToInt().coerceIn(140, maxW)

        val fm1 = titlePaint.fontMetrics
        val fm2 = subPaint.fontMetrics
        val h1 = fm1.descent - fm1.ascent
        val h2 = fm2.descent - fm2.ascent
        val boxH = (padY * 2f + h1 + lineGap + h2).roundToInt().coerceAtLeast(44)

        val bmp = if (existing != null && existing.width == boxW && existing.height == boxH) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888).also {
                bitmap = it
                canvas = Canvas(it)
            }
        }

        val g = canvas!!
        g.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val inset = borderPaint.strokeWidth * 0.5f + 1f
        val rect = RectF(inset, inset, boxW - inset, boxH - inset)
        val radius = boxH * 0.44f
        g.drawRoundRect(rect, radius, radius, bgPaint)
        g.drawRoundRect(rect, radius, radius, borderPaint)

        val cx = boxW * 0.5f
        val y1 = padY - fm1.ascent
        val y2 = padY + h1 + lineGap - fm2.ascent
        g.drawText(lines.first, cx, y1, titlePaint)
        g.drawText(lines.second, cx, y2, subPaint)

        lastKey = key
        return HudRenderResult(bmp, changed = true, boxW, boxH)
    }

    private fun formatLines(state: SeaState, simHours: Double, elDeg: Double): Pair<String, String> {
        val h = ((simHours % 24.0) + 24.0) % 24.0
        val dayPeriod = when {
            elDeg <= -6.0 -> "Ночь"
            elDeg < 2.5 && h < 12.0 -> "Рассвет"
            elDeg < 2.5 && h >= 12.0 -> "Сумерки"
            elDeg < 7.0 && h >= 12.0 -> "Закат"
            h < 11.5 -> "Утро"
            h < 17.0 -> "День"
            else -> "Вечер"
        }

        val cityPart = state.cityName.trim()
        val tempPart = state.temperatureC?.let { t ->
            val rounded = t.roundToInt()
            if (rounded > 0) "+$rounded°" else "$rounded°"
        }

        val titleParts = buildList {
            if (cityPart.isNotEmpty()) add(cityPart)
            if (tempPart != null) add(tempPart)
            add(dayPeriod)
        }
        val line1 = titleParts.joinToString(" · ")

        val condition = describeWeather(state)
        val precip = when {
            state.precipitationMm >= 0.1 -> "осадки ${"%.1f".format(state.precipitationMm)} мм/ч"
            state.storm > 0.5 -> "грозовые осадки"
            state.rain >= 0.55 -> "сильный дождь"
            state.rain >= 0.2 -> "дождь"
            state.rain >= 0.04 -> "небольшие осадки"
            else -> "без осадков"
        }
        val windDesc = when {
            state.wind >= 0.75 -> "штормовой ветер"
            state.wind >= 0.45 -> "свежий ветер"
            state.wind >= 0.18 -> "лёгкий бриз"
            else -> "штиль"
        }
        val line2 = "$condition · $precip · $windDesc"
        return line1 to line2
    }

    private fun describeWeather(state: SeaState): String {
        val code = if (state.autoMode) state.weatherCode else -1
        if (code >= 0) {
            return when (code) {
                0 -> "Ясно"
                1 -> "Малооблачно"
                2 -> "Переменная облачность"
                3 -> "Пасмурно"
                45, 48 -> "Туман"
                51, 53, 55, 56, 57 -> "Морось"
                61, 63, 65, 66, 67, 80, 81, 82 -> "Дождь"
                71, 73, 75, 77, 85, 86 -> "Снег"
                95, 96, 99 -> "Гроза"
                else -> if (state.cloud > 0.75) "Пасмурно" else "Облачно"
            }
        }
        return when {
            state.storm > 0.5 -> "Гроза"
            state.rain >= 0.5 -> "Ливень"
            state.rain >= 0.08 -> "Дождь"
            state.fog >= 0.35 -> "Туман"
            state.cloud >= 0.78 -> "Пасмурно"
            state.cloud >= 0.42 -> "Облачно"
            state.cloud >= 0.18 -> "Малооблачно"
            else -> "Ясно"
        }
    }
}

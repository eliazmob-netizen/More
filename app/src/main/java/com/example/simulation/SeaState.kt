package com.example.simulation

enum class QualityMode(val scale: Float, val targetFps: Int) {
    AUTO(0.6f, 30),
    ECO(0.5f, 20),
    MAX(1.0f, 30),
}

enum class WeatherPreset(
    val label: String,
    val wind: Double,
    val cloud: Double,
    val rain: Double,
    val fog: Double,
    val storm: Double,
) {
    CALM("Штиль", 0.03, 0.12, 0.0, 0.0, 0.0),
    BREEZE("Бриз", 0.22, 0.30, 0.0, 0.0, 0.0),
    CLEAR("Ясно", 0.30, 0.05, 0.0, 0.0, 0.0),
    CLOUDY("Облачно", 0.35, 0.55, 0.0, 0.0, 0.0),
    OVERCAST("Пасмурно", 0.45, 0.95, 0.0, 0.10, 0.0),
    RAIN("Дождь", 0.60, 0.95, 0.65, 0.25, 0.0),
    STORM("Шторм", 1.0, 1.0, 0.90, 0.30, 1.0),
    FOG("Туман", 0.08, 0.70, 0.0, 0.85, 0.0),
}

enum class TimePreset(val label: String) {
    DAWN("Рассвет"),
    NOON("Полдень"),
    SUNSET("Закат"),
    DUSK("Сумерки"),
    NIGHT("Ночь"),
}

data class SunTimes(
    val sunriseHours: Double,
    val sunsetHours: Double,
)

data class SolarPos(
    val el: Double,
    val az: Double,
)

data class SeaState(
    val t: Double = 12.0,
    val lat: Double = 55.75,
    val lon: Double = 37.62,
    val utcOffsetSeconds: Int? = null,
    val cam: Double = 250.0,
    val camEff: Double = 250.0,
    val windDir: Double = 50.0,
    val wind: Double = 0.22,
    val cloud: Double = 0.30,
    val rain: Double = 0.0,
    val fog: Double = 0.0,
    val storm: Double = 0.0,
    val flash: Double = 0.0,
    val realTime: Boolean = true,
    val fastDay: Boolean = false,
    val doy: Int = 1,
    val bolt: Double = 0.5,
    val lhOn: Boolean = true,
    val lhRotate: Boolean = true,
    val shipOn: Boolean = true,
    val birdsOn: Boolean = true,
    val dropsOn: Boolean = true,
    val fastShip: Boolean = false,
    val autoMode: Boolean = false,
    val gyroMode: Boolean = false,
    val yawG: Double = 0.0,
    val yawD: Double = 0.0,
    val pitG: Double = 0.0,
    val pitD: Double = 0.0,
    val roll: Double = 0.0,
    val shipRel: Double? = 2.0,
    val qualityMode: QualityMode = QualityMode.AUTO,
)

data class FrameParams(
    val bow: Double,
    val lh: Double,
    val lhRotate: Boolean = true,
    val lhFixedB: Double = 0.0,
    val ship: Double,
    val night2: Double,
    val obj: FloatArray,
    val bolt: Double,
    val sun: DoubleArray,
    val sunL: DoubleArray,
    val sunD: DoubleArray,
    val moon: DoubleArray,
    val moonPh: Double,
    val moonL: Double,
    val exp: Double,
    val pole: DoubleArray,
    val starRot: Double,
    val star: Double,
    val elDeg: Double,
    val over: Double,
    val wdir: Double,
    val cloud: Double,
    val wind: Double,
    val rain: Double,
    val fog: Double,
    val flash: Double,
    val amb: DoubleArray = doubleArrayOf(0.1, 0.1, 0.1),
)

data class LightModel(
    val dayF: Double,
    val night: Double,
    val sun: DoubleArray,
    val amb: DoubleArray,
    val sunL: DoubleArray,
    val hazeColor: DoubleArray,
) {
    fun shade(a: DoubleArray, l: Double): DoubleArray {
        val dl = maxOf(0.0, l - 0.3) * 0.85
        return DoubleArray(3) { k ->
            val x = a[k] * (amb[k] * 2.2 + sunL[k] * dl)
            disp(x)
        }
    }

    fun haze(c: DoubleArray, k: Double): DoubleArray {
        return DoubleArray(3) { i -> c[i] + (hazeColor[i] - c[i]) * k }
    }

    companion object {
        fun aces(x: Double): Double {
            val v = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)
            return v.coerceIn(0.0, 1.0)
        }

        fun disp(x: Double): Double = Math.pow(aces(x), 1.0 / 2.2)
    }
}

data class FpsStats(
    val fps: Int = 0,
    val sceneWidth: Int = 0,
    val sceneHeight: Int = 0,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)

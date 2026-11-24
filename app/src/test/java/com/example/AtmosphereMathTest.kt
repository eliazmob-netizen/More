package com.example

import com.example.simulation.AtmosphereMath
import com.example.simulation.GullState
import com.example.simulation.SeaState
import com.example.simulation.ShipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereMathTest {

    @Test
    fun solarElevationIsPositiveAtNoonInSummer() {
        val sp = AtmosphereMath.solar(h = 12.0, lat = 55.75, doy = 172)
        assertTrue("Expected positive solar elevation at summer noon", sp.el > 0.8)
    }

    @Test
    fun sunTimesOrdersSunriseBeforeSunset() {
        val st = AtmosphereMath.sunTimes(lat = 55.75, doy = 172)
        assertTrue(st.sunriseHours in 2.0..8.0)
        assertTrue(st.sunsetHours in 16.0..23.0)
        assertTrue(st.sunriseHours < st.sunsetHours)
    }

    @Test
    fun makeNrmProducesValidRgba256x256Texture() {
        val nrm = AtmosphereMath.makeNrm()
        assertEquals(256 * 256 * 4, nrm.size)
        val hasVariation = nrm.indices.any { idx -> idx % 4 == 2 && (nrm[idx].toInt() and 0xFF) != 0 }
        assertTrue("Foam channel should contain non-zero values", hasVariation)
    }

    @Test
    fun bakeLutPopulatesOpaqueRgbaRows() {
        val out = ByteArray(AtmosphereMath.LW * AtmosphereMath.LH * 4)
        val sd = doubleArrayOf(0.0, 0.5, 0.866)
        for (j in 0 until AtmosphereMath.LH) {
            AtmosphereMath.bakeRow(sd, 0.9, out, j)
        }
        for (i in 0 until AtmosphereMath.LW * AtmosphereMath.LH) {
            assertEquals(255, out[i * 4 + 3].toInt() and 0xFF)
        }
    }

    @Test
    fun frameParamsCalculatesRainbowAndLighting() {
        val state = SeaState(t = 17.5, lat = 55.75, doy = 172, rain = 0.2, cloud = 0.4, wind = 0.3)
        val params = AtmosphereMath.frameParams(state, 1_700_000_000_000L)
        assertTrue(params.bow >= 0.0)
        assertEquals(3, params.sun.size)
        assertEquals(3, params.obj.size)
    }

    @Test
    fun gullAndShipStepAdvancePosition() {
        val gull = GullState.spawn()
        val x0 = gull.x
        val z0 = gull.z
        gull.step(0.1)
        assertTrue(gull.x != x0 || gull.z != z0)

        val ship = ShipState.spawn(near = true, fast = true)
        val sx0 = ship.x
        val sz0 = ship.z
        ship.step(0.5)
        assertTrue(ship.x != sx0 || ship.z != sz0)
    }
}

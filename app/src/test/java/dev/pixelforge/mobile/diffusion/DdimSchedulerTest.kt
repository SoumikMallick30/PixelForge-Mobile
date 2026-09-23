package dev.pixelforge.mobile.diffusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdimSchedulerTest {
    @Test fun `schedule has requested descending steps`() {
        val scheduler = DdimScheduler(20)
        assertEquals(20, scheduler.timesteps.size)
        assertTrue((0 until scheduler.timesteps.lastIndex).all { i ->
            scheduler.timesteps[i] > scheduler.timesteps[i + 1]
        })
    }

    @Test fun `noise and reverse step preserve tensor shape and finite values`() {
        val scheduler = DdimScheduler(20)
        val clean = FloatArray(32) { .25f }
        val noise = FloatArray(32) { -.5f }
        val timestep = scheduler.timesteps[4]
        val noisy = scheduler.addNoise(clean, noise, timestep)
        val stepped = scheduler.step(noise, timestep, noisy)
        assertEquals(clean.size, noisy.size)
        assertEquals(clean.size, stepped.size)
        assertTrue(noisy.all(Float::isFinite))
        assertTrue(stepped.all(Float::isFinite))
    }
}

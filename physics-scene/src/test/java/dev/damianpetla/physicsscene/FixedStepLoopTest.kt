package dev.damianpetla.physicsscene

import dev.damianpetla.physicsscene.engine.FixedStepAccumulator
import org.junit.Assert.assertEquals
import org.junit.Test

class FixedStepLoopTest {

    @Test
    fun accumulatesAndExecutesFixedSteps() {
        val accumulator = FixedStepAccumulator(
            fixedStepSeconds = 1f / 60f,
            maxSubSteps = 5,
        )
        var steps = 0

        accumulator.onFrame(0.010f) { steps++ }
        accumulator.onFrame(0.010f) { steps++ }
        accumulator.onFrame(0.010f) { steps++ }

        assertEquals(1, steps)
    }

    @Test
    fun clampsWhenFrameStalls() {
        val accumulator = FixedStepAccumulator(
            fixedStepSeconds = 1f / 60f,
            maxSubSteps = 2,
        )
        var steps = 0

        accumulator.onFrame(0.3f) { steps++ }

        assertEquals(2, steps)
    }
}

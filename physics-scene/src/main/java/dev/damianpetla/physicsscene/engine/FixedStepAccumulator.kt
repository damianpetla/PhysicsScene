package dev.damianpetla.physicsscene.engine

internal class FixedStepAccumulator(
    private val fixedStepSeconds: Float,
    private val maxSubSteps: Int,
) {
    private var accumulatorSeconds: Float = 0f

    fun onFrame(deltaSeconds: Float, step: (Float) -> Unit) {
        val clampedDelta = deltaSeconds.coerceIn(0f, 0.1f)
        accumulatorSeconds += clampedDelta

        var subSteps = 0
        while (accumulatorSeconds >= fixedStepSeconds && subSteps < maxSubSteps) {
            step(fixedStepSeconds)
            accumulatorSeconds -= fixedStepSeconds
            subSteps++
        }

        if (subSteps == maxSubSteps && accumulatorSeconds >= fixedStepSeconds) {
            accumulatorSeconds = 0f
        }
    }
}

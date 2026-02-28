package dev.damianpetla.physicsscene

import androidx.compose.ui.geometry.Offset
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsSceneStateTest {

    @Test
    fun bodyAndShardSnapshots_areObservableAndResettable() {
        val state = PhysicsSceneState(
            pixelsPerMeter = 100f,
            gravityPxPerSecondSq = Offset.Zero,
            fixedStepHz = 60,
            maxSubStepsPerFrame = 5,
        )
        val body = mapOf(
            "a" to PhysicsBodySnapshot(
                centerPx = Offset(10f, 20f),
                angleRad = 0.3f,
            ),
        )
        val shards = listOf(
            PhysicsShardSnapshot(
                id = 3L,
                ownerId = "a",
                centerPx = Offset(4f, 8f),
            ),
        )

        state.setBodySnapshots(body)
        state.setShardSnapshots(shards)

        assertEquals(body, state.bodySnapshots.value)
        assertEquals(shards, state.shardSnapshots.value)
        assertEquals(body["a"], state.bodySnapshot("a"))
        assertEquals(shards, state.shardSnapshots(ownerId = "a"))

        state.resetAllLifecycleState()

        assertTrue(state.bodySnapshots.value.isEmpty())
        assertTrue(state.shardSnapshots.value.isEmpty())
    }

    @Test
    fun respawnBody_setsIdleAndAllowsActivationCommand() = runBlocking {
        val state = PhysicsSceneState(
            pixelsPerMeter = 100f,
            gravityPxPerSecondSq = Offset.Zero,
            fixedStepHz = 60,
            maxSubStepsPerFrame = 5,
        )
        state.setLifecycle("node", PhysicsLifecycleState.Removed)

        state.respawnBody("node")
        state.activateBody("node")

        assertEquals(PhysicsLifecycleState.Idle, state.lifecycleOf("node"))
        assertEquals(PhysicsCommand.Respawn("node"), state.commandFlow.first())
        assertEquals(PhysicsCommand.Activate("node"), state.commandFlow.first())
    }
}

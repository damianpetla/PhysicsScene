package dev.damianpetla.physicsscene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.badlogic.gdx.box2d.Box2d
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private object Box2dRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            Box2d.initialize()
            initialized = true
        }
    }
}

internal sealed interface PhysicsCommand {
    data class Activate(val id: PhysicsId) : PhysicsCommand
    data class Explode(val id: PhysicsId) : PhysicsCommand
    data class Remove(val id: PhysicsId) : PhysicsCommand
    data class ApplyLinearImpulse(
        val id: PhysicsId,
        val impulsePx: Offset,
    ) : PhysicsCommand
    data class PulseFromBody(
        val originId: PhysicsId,
        val radiusPx: Float,
        val impulse: Float,
    ) : PhysicsCommand
    data class AttractShards(
        val ownerId: PhysicsId,
        val targetPx: Offset,
        val impulsePx: Float,
        val maxDistancePx: Float,
    ) : PhysicsCommand
    data class RemoveShard(
        val shardId: Long,
    ) : PhysicsCommand
    data object ResetScene : PhysicsCommand
}

data class PhysicsBodySnapshot(
    val centerPx: Offset,
    val angleRad: Float,
)

data class PhysicsShardSnapshot(
    val id: Long,
    val ownerId: PhysicsId,
    val centerPx: Offset,
)

@Stable
class PhysicsSceneState internal constructor(
    internal val pixelsPerMeter: Float,
    internal val gravityPxPerSecondSq: Offset,
    internal val fixedStepHz: Int,
    internal val maxSubStepsPerFrame: Int,
) {
    private val lifecycleStates = mutableStateMapOf<PhysicsId, PhysicsLifecycleState>()
    private val commands = Channel<PhysicsCommand>(capacity = Channel.UNLIMITED)
    private val bodySnapshots = LinkedHashMap<PhysicsId, PhysicsBodySnapshot>()
    private var shardSnapshots: List<PhysicsShardSnapshot> = emptyList()

    internal var frameTickNanos by mutableLongStateOf(0L)
    internal val commandFlow: Flow<PhysicsCommand> = commands.receiveAsFlow()

    internal fun lifecycleOf(id: PhysicsId): PhysicsLifecycleState {
        return lifecycleStates[id] ?: PhysicsLifecycleState.Idle
    }

    fun activateBody(id: PhysicsId) {
        if (lifecycleOf(id) != PhysicsLifecycleState.Idle) {
            return
        }
        commands.trySend(PhysicsCommand.Activate(id))
    }

    fun explode(id: PhysicsId) {
        commands.trySend(PhysicsCommand.Explode(id))
    }

    fun remove(id: PhysicsId) {
        commands.trySend(PhysicsCommand.Remove(id))
    }

    fun applyLinearImpulse(
        id: PhysicsId,
        impulsePx: Offset,
    ) {
        if (impulsePx == Offset.Zero) return
        commands.trySend(
            PhysicsCommand.ApplyLinearImpulse(
                id = id,
                impulsePx = impulsePx,
            ),
        )
    }

    fun pulseFromBody(
        originId: PhysicsId,
        radiusPx: Float = 300f,
        impulse: Float = 0.7f,
    ) {
        if (radiusPx <= 0f || impulse <= 0f) return
        commands.trySend(
            PhysicsCommand.PulseFromBody(
                originId = originId,
                radiusPx = radiusPx,
                impulse = impulse,
            ),
        )
    }

    fun attractShards(
        ownerId: PhysicsId,
        targetPx: Offset,
        impulsePx: Float = 16f,
        maxDistancePx: Float = 320f,
    ) {
        if (impulsePx <= 0f || maxDistancePx <= 0f) return
        commands.trySend(
            PhysicsCommand.AttractShards(
                ownerId = ownerId,
                targetPx = targetPx,
                impulsePx = impulsePx,
                maxDistancePx = maxDistancePx,
            ),
        )
    }

    fun removeShard(shardId: Long) {
        commands.trySend(PhysicsCommand.RemoveShard(shardId))
    }

    fun resetScene() {
        commands.trySend(PhysicsCommand.ResetScene)
    }

    fun bodySnapshot(id: PhysicsId): PhysicsBodySnapshot? {
        return bodySnapshots[id]
    }

    fun shardSnapshots(ownerId: PhysicsId? = null): List<PhysicsShardSnapshot> {
        if (ownerId == null) return shardSnapshots
        return shardSnapshots.filter { it.ownerId == ownerId }
    }

    internal fun setLifecycle(id: PhysicsId, lifecycle: PhysicsLifecycleState) {
        lifecycleStates[id] = lifecycle
    }

    internal fun setBodySnapshots(snapshots: Map<PhysicsId, PhysicsBodySnapshot>) {
        bodySnapshots.clear()
        bodySnapshots.putAll(snapshots)
    }

    internal fun setShardSnapshots(snapshots: List<PhysicsShardSnapshot>) {
        shardSnapshots = snapshots
    }

    internal fun resetAllLifecycleState() {
        lifecycleStates.clear()
        bodySnapshots.clear()
        shardSnapshots = emptyList()
    }
}

@Composable
fun rememberPhysicsSceneState(
    pixelsPerMeter: Float = 100f,
    gravityPxPerSecondSq: Offset = Offset(0f, 2_200f),
    fixedStepHz: Int = 60,
    maxSubStepsPerFrame: Int = 5,
): PhysicsSceneState {
    Box2dRuntime.ensureInitialized()
    return remember(pixelsPerMeter, gravityPxPerSecondSq, fixedStepHz, maxSubStepsPerFrame) {
        PhysicsSceneState(
            pixelsPerMeter = pixelsPerMeter,
            gravityPxPerSecondSq = gravityPxPerSecondSq,
            fixedStepHz = fixedStepHz,
            maxSubStepsPerFrame = maxSubStepsPerFrame,
        )
    }
}

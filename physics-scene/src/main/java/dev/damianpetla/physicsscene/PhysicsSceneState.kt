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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class Respawn(val id: PhysicsId) : PhysicsCommand
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

/**
 * Snapshot of a registered body at the latest simulation step.
 *
 * @param centerPx Current body center in scene-local pixels.
 * @param angleRad Current body angle in radians.
 */
data class PhysicsBodySnapshot(
    val centerPx: Offset,
    val angleRad: Float,
)

/**
 * Snapshot of a rendered shard at the latest simulation step.
 *
 * @param id Runtime shard id unique within the current scene generation.
 * @param ownerId Id of the body that spawned this shard.
 * @param centerPx Current shard center in scene-local pixels.
 */
data class PhysicsShardSnapshot(
    val id: Long,
    val ownerId: PhysicsId,
    val centerPx: Offset,
)

/**
 * Command and snapshot state holder for [PhysicsScene].
 *
 * Create this object with [rememberPhysicsSceneState]. The state exposes command methods
 * (`activateBody`, `explode`, `remove`, `respawnBody`, and impulse helpers) and snapshot streams
 * for bodies and shards.
 */
@Stable
class PhysicsSceneState internal constructor(
    internal val pixelsPerMeter: Float,
    internal val gravityPxPerSecondSq: Offset,
    internal val fixedStepHz: Int,
    internal val maxSubStepsPerFrame: Int,
) {
    private val lifecycleStates = mutableStateMapOf<PhysicsId, PhysicsLifecycleState>()
    private val commands = Channel<PhysicsCommand>(capacity = Channel.UNLIMITED)
    private val _bodySnapshots = MutableStateFlow<Map<PhysicsId, PhysicsBodySnapshot>>(emptyMap())
    private val _shardSnapshots = MutableStateFlow<List<PhysicsShardSnapshot>>(emptyList())

    internal var frameTickNanos by mutableLongStateOf(0L)
    internal val commandFlow: Flow<PhysicsCommand> = commands.receiveAsFlow()

    /**
     * Reactive stream of current body snapshots keyed by body id.
     */
    val bodySnapshots: StateFlow<Map<PhysicsId, PhysicsBodySnapshot>> = _bodySnapshots.asStateFlow()

    /**
     * Reactive stream of all current shard snapshots.
     */
    val shardSnapshots: StateFlow<List<PhysicsShardSnapshot>> = _shardSnapshots.asStateFlow()

    internal fun lifecycleOf(id: PhysicsId): PhysicsLifecycleState {
        return lifecycleStates[id] ?: PhysicsLifecycleState.Idle
    }

    /**
     * Activates a registered body.
     *
     * The command is ignored when the body lifecycle is not [PhysicsLifecycleState.Idle].
     *
     * @param id Body id passed to `Modifier.physicsBody`.
     */
    fun activateBody(id: PhysicsId) {
        if (lifecycleOf(id) != PhysicsLifecycleState.Idle) {
            return
        }
        commands.trySend(PhysicsCommand.Activate(id))
    }

    /**
     * Starts shattering for a registered body when explosion is configured.
     *
     * If the body has no shatter data available at runtime, the body is removed.
     *
     * @param id Body id passed to `Modifier.physicsBody`.
     */
    fun explode(id: PhysicsId) {
        commands.trySend(PhysicsCommand.Explode(id))
    }

    /**
     * Removes a body and its owned shards from the simulation.
     *
     * @param id Body id passed to `Modifier.physicsBody`.
     */
    fun remove(id: PhysicsId) {
        commands.trySend(PhysicsCommand.Remove(id))
    }

    /**
     * Clears removed tombstone state for a body and restores [PhysicsLifecycleState.Idle].
     *
     * Use this method when you want to spawn the same body id again without calling [resetScene].
     * The body will be recreated from the current layout on the next sync frame.
     *
     * @param id Body id passed to `Modifier.physicsBody`.
     */
    fun respawnBody(id: PhysicsId) {
        setLifecycle(id, PhysicsLifecycleState.Idle)
        commands.trySend(PhysicsCommand.Respawn(id))
    }

    /**
     * Applies linear impulse to a body center.
     *
     * @param id Body id passed to `Modifier.physicsBody`.
     * @param impulsePx Screen-space impulse vector in pixel units. `Offset.Zero` is ignored.
     */
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

    /**
     * Applies radial impulse around an origin body.
     *
     * @param originId Body id used as impulse origin.
     * @param radiusPx Radius in pixels. Values `<= 0` are ignored.
     * @param impulse Radial impulse strength in physics world units. Values `<= 0` are ignored.
     */
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

    /**
     * Attracts shards toward a target point.
     *
     * @param ownerId Body id that owns targeted shards.
     * @param targetPx Attraction target in scene-local pixels.
     * @param impulsePx Attraction impulse magnitude in pixel units. Values `<= 0` are ignored.
     * @param maxDistancePx Maximum attraction distance in pixels. Values `<= 0` are ignored.
     */
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

    /**
     * Removes one shard by runtime id.
     *
     * @param shardId Id from [shardSnapshots].
     */
    fun removeShard(shardId: Long) {
        commands.trySend(PhysicsCommand.RemoveShard(shardId))
    }

    /**
     * Resets the whole scene runtime state.
     *
     * Clears all bodies, shards, lifecycle states, snapshots, and shard id sequence.
     */
    fun resetScene() {
        commands.trySend(PhysicsCommand.ResetScene)
    }

    /**
     * Returns latest body snapshot for one id.
     *
     * @param id Body id.
     * @return Snapshot for `id` or `null` when body is not present.
     */
    fun bodySnapshot(id: PhysicsId): PhysicsBodySnapshot? {
        return bodySnapshots.value[id]
    }

    /**
     * Returns latest shard snapshots, optionally filtered by owner.
     *
     * @param ownerId Optional owner id filter. `null` returns all shards.
     */
    fun shardSnapshots(ownerId: PhysicsId? = null): List<PhysicsShardSnapshot> {
        val snapshots = shardSnapshots.value
        if (ownerId == null) return snapshots
        return snapshots.filter { it.ownerId == ownerId }
    }

    internal fun setLifecycle(id: PhysicsId, lifecycle: PhysicsLifecycleState) {
        lifecycleStates[id] = lifecycle
    }

    internal fun setBodySnapshots(snapshots: Map<PhysicsId, PhysicsBodySnapshot>) {
        _bodySnapshots.value = snapshots.toMap()
    }

    internal fun setShardSnapshots(snapshots: List<PhysicsShardSnapshot>) {
        _shardSnapshots.value = snapshots.toList()
    }

    internal fun resetAllLifecycleState() {
        lifecycleStates.clear()
        _bodySnapshots.value = emptyMap()
        _shardSnapshots.value = emptyList()
    }
}

/**
 * Remembers and returns a [PhysicsSceneState].
 *
 * @param pixelsPerMeter Scene scale used for Box2D conversion. Must stay `> 0`.
 * @param gravityPxPerSecondSq Gravity vector in pixels per second squared.
 * @param fixedStepHz Fixed simulation frequency in Hz.
 * @param maxSubStepsPerFrame Maximum number of fixed substeps executed in one frame.
 */
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

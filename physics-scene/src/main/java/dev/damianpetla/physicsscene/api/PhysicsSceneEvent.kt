package dev.damianpetla.physicsscene.api

import dev.damianpetla.physicsscene.PhysicsScene
import dev.damianpetla.physicsscene.PhysicsSceneState

/**
 * Stable identifier of a body registered in [PhysicsScene].
 *
 * The same id should be reused across recompositions for the same logical body.
 */
typealias PhysicsId = String

/**
 * Base type for runtime events emitted from [PhysicsScene].
 *
 * Events are emitted during physics stepping after commands issued through [PhysicsSceneState].
 * Use these callbacks to drive UI-side state machines, analytics, or effect orchestration.
 */
sealed interface PhysicsSceneEvent

/**
 * Emitted when a body is activated and starts participating in dynamic simulation.
 *
 * @param id Body id that was activated.
 */
data class BodyActivated(
    val id: PhysicsId,
) : PhysicsSceneEvent

/**
 * Emitted when a body starts shattering into shards.
 *
 * @param id Body id that entered the shattering phase.
 */
data class BodyShatteringStarted(
    val id: PhysicsId,
) : PhysicsSceneEvent

/**
 * Emitted when a body is removed from the world.
 *
 * @param id Body id that was removed.
 */
data class BodyRemoved(
    val id: PhysicsId,
) : PhysicsSceneEvent

/**
 * Emitted when an individual shard is hit by a dynamic body and removed.
 *
 * @param ownerId Id of the source body that created the shard.
 * @param hitterId Id of the dynamic body that hit the shard.
 * @param shardId Runtime shard id. Ids are reset after [PhysicsSceneState.resetScene].
 */
data class ShardHit(
    val ownerId: PhysicsId,
    val hitterId: PhysicsId,
    val shardId: Long,
) : PhysicsSceneEvent

/**
 * Emitted when an individual shard reaches floor cleanup threshold and is removed.
 *
 * @param ownerId Id of the source body that created the shard.
 * @param shardId Runtime shard id. Ids are reset after [PhysicsSceneState.resetScene].
 */
data class ShardDropped(
    val ownerId: PhysicsId,
    val shardId: Long,
) : PhysicsSceneEvent

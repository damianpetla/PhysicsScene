package dev.damianpetla.physicsscene.api

/**
 * Collider shape used for generated shards.
 */
enum class ShardColliderShape {
    /**
     * Axis-aligned box shard collider.
     */
    Box,

    /**
     * Circle shard collider (radius from min shard dimension).
     */
    Circle,
}

/**
 * Parameters controlling shard generation and launch behavior.
 *
 * @param enabled Enables shattering pipeline. When `false`, explode removes body without shards.
 * @param shardsRows Requested number of shard rows. Values `< 1` are clamped to `1`.
 * @param shardsCols Requested number of shard columns. Values `< 1` are clamped to `1`.
 * @param squareShards When `true`, runtime may adjust columns to preserve square-ish shard cells.
 * @param shardColliderShape Physical collider shape assigned to each shard.
 * @param shardTtlMs Shard lifetime in milliseconds. `<= 0` keeps shards until explicit removal/floor cleanup.
 * @param impulseMin Minimum launch impulse per shard in Box2D world units.
 * @param impulseMax Maximum launch impulse per shard in Box2D world units.
 */
data class ExplosionSpec(
    val enabled: Boolean = true,
    val shardsRows: Int = 5,
    val shardsCols: Int = 5,
    val squareShards: Boolean = true,
    val shardColliderShape: ShardColliderShape = ShardColliderShape.Box,
    val shardTtlMs: Int = -1,
    val impulseMin: Float = 0.45f,
    val impulseMax: Float = 1.8f,
)

package dev.damianpetla.physicsscene.api

enum class ShardColliderShape {
    Box,
    Circle,
}

data class ExplosionSpec(
    val enabled: Boolean = true,
    val shardsRows: Int = 5,
    val shardsCols: Int = 5,
    val squareShards: Boolean = true,
    val shardColliderShape: ShardColliderShape = ShardColliderShape.Box,
    // <= 0 means keep shard bodies in the physics world until explicitly removed.
    val shardTtlMs: Int = -1,
    val impulseMin: Float = 0.45f,
    val impulseMax: Float = 1.8f,
)

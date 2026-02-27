package dev.damianpetla.physicsscene.api

interface PhysicsEffect {
    fun bodySpec(baseSpec: PhysicsBodySpec = PhysicsBodySpec()): PhysicsBodySpec
}

data class FallingShatterEffect(
    val density: Float = 1.2f,
    val friction: Float = 0.18f,
    val restitution: Float = 0.42f,
    val linearDamping: Float = 0.01f,
    val angularDamping: Float = 0.02f,
    val shardsRows: Int = 5,
    val shardsCols: Int = 5,
    val squareShards: Boolean = true,
    val shardColliderShape: ShardColliderShape = ShardColliderShape.Box,
    val shardTtlMs: Int = -1,
    val impulseMin: Float = 0.06f,
    val impulseMax: Float = 0.18f,
) : PhysicsEffect {
    override fun bodySpec(baseSpec: PhysicsBodySpec): PhysicsBodySpec {
        return baseSpec.copy(
            bodyType = PhysicsBodyType.Static,
            density = density,
            friction = friction,
            restitution = restitution,
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            explodeOnFirstImpact = true,
            explosionSpec = ExplosionSpec(
                shardsRows = shardsRows.coerceAtLeast(1),
                shardsCols = shardsCols.coerceAtLeast(1),
                squareShards = squareShards,
                shardColliderShape = shardColliderShape,
                shardTtlMs = shardTtlMs,
                impulseMin = impulseMin,
                impulseMax = impulseMax.coerceAtLeast(impulseMin),
            ),
        )
    }
}

data class CenterBurstEffect(
    val density: Float = 1.1f,
    val friction: Float = 0.12f,
    val restitution: Float = 0.56f,
    val linearDamping: Float = 0.01f,
    val angularDamping: Float = 0.02f,
    val shardsRows: Int = 6,
    val shardsCols: Int = 6,
    val squareShards: Boolean = true,
    val shardTtlMs: Int = -1,
    val impulseMin: Float = 0.18f,
    val impulseMax: Float = 0.42f,
) : PhysicsEffect {
    override fun bodySpec(baseSpec: PhysicsBodySpec): PhysicsBodySpec {
        return baseSpec.copy(
            bodyType = PhysicsBodyType.Static,
            density = density,
            friction = friction,
            restitution = restitution,
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            explodeOnFirstImpact = false,
            explosionSpec = ExplosionSpec(
                shardsRows = shardsRows.coerceAtLeast(1),
                shardsCols = shardsCols.coerceAtLeast(1),
                squareShards = squareShards,
                shardColliderShape = ShardColliderShape.Circle,
                shardTtlMs = shardTtlMs,
                impulseMin = impulseMin,
                impulseMax = impulseMax.coerceAtLeast(impulseMin),
            ),
        )
    }
}

fun interface CustomEffect : PhysicsEffect {
    override fun bodySpec(baseSpec: PhysicsBodySpec): PhysicsBodySpec
}

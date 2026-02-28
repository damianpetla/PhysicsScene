package dev.damianpetla.physicsscene.api

/**
 * High-level preset that transforms [baseSpec][PhysicsBodySpec] into a final body configuration.
 */
interface PhysicsEffect {
    /**
     * Builds final low-level body spec for this effect.
     *
     * @param baseSpec Base configuration to be used as input.
     * @return Final [PhysicsBodySpec] used by `Modifier.physicsBody(id, effect, baseSpec)`.
     */
    fun bodySpec(baseSpec: PhysicsBodySpec = PhysicsBodySpec()): PhysicsBodySpec
}

/**
 * Falling preset that shatters on first allowed impact.
 *
 * @param density Body density passed to Box2D fixture.
 * @param friction Surface friction coefficient.
 * @param restitution Surface restitution coefficient.
 * @param linearDamping Linear damping applied each step.
 * @param angularDamping Angular damping applied each step.
 * @param shardsRows Requested shard rows. Values `< 1` are clamped to `1`.
 * @param shardsCols Requested shard columns. Values `< 1` are clamped to `1`.
 * @param squareShards Enables runtime square-shard column fitting.
 * @param shardColliderShape Collider shape used by generated shards.
 * @param shardTtlMs Shard lifetime in milliseconds. `<= 0` keeps shards alive until explicit cleanup.
 * @param impulseMin Minimum shard launch impulse in world units.
 * @param impulseMax Maximum shard launch impulse in world units. Values below `impulseMin` are clamped.
 */
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

/**
 * Tap-to-burst preset for controlled center explosions (no impact-triggered shatter).
 *
 * @param density Body density passed to Box2D fixture.
 * @param friction Surface friction coefficient.
 * @param restitution Surface restitution coefficient.
 * @param linearDamping Linear damping applied each step.
 * @param angularDamping Angular damping applied each step.
 * @param shardsRows Requested shard rows. Values `< 1` are clamped to `1`.
 * @param shardsCols Requested shard columns. Values `< 1` are clamped to `1`.
 * @param squareShards Enables runtime square-shard column fitting.
 * @param shardTtlMs Shard lifetime in milliseconds. `<= 0` keeps shards alive until explicit cleanup.
 * @param impulseMin Minimum shard launch impulse in world units.
 * @param impulseMax Maximum shard launch impulse in world units. Values below `impulseMin` are clamped.
 */
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

/**
 * Custom high-level effect adapter for advanced consumers.
 */
fun interface CustomEffect : PhysicsEffect {
    override fun bodySpec(baseSpec: PhysicsBodySpec): PhysicsBodySpec
}

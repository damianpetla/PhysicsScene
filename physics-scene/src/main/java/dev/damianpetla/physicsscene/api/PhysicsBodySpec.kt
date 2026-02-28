package dev.damianpetla.physicsscene.api

/**
 * Box2D body type used for a registered composable.
 */
enum class PhysicsBodyType {
    /**
     * Static body that does not move because of simulation forces.
     */
    Static,

    /**
     * Dynamic body affected by gravity, impulses and collisions.
     */
    Dynamic,

    /**
     * Kinematic body driven by explicit velocity updates.
     */
    Kinematic,
}

/**
 * Low-level body configuration used by `Modifier.physicsBody(...)`.
 *
 * Defaults are production-safe for UI-like interactions and can be overridden per body.
 *
 * @param bodyType Initial body type used when the body is created.
 * @param density Fixture density. Must be `> 0` for realistic dynamic behavior.
 * @param friction Contact friction coefficient. Typical range: `0.0..1.0`.
 * @param restitution Contact bounciness coefficient. Typical range: `0.0..1.0`.
 * @param linearDamping Linear damping applied every step. Typical range: `0.0..1.0`.
 * @param angularDamping Angular damping applied every step. Typical range: `0.0..1.0`.
 * @param isSensor When `true`, body receives sensor events and does not produce physical response.
 * @param removeOnFloorContact When `true`, body is removed after reaching floor cleanup threshold.
 * @param explodeOnFirstImpact When `true`, first allowed impact triggers shattering.
 * @param explodeOnImpactByIds Optional allow-list of hitter ids that may trigger impact explosion.
 * Empty set means any valid impact source can trigger explosion.
 * @param explosionSpec Shard generation parameters used when explosion is executed.
 */
data class PhysicsBodySpec(
    val bodyType: PhysicsBodyType = PhysicsBodyType.Static,
    val density: Float = 1f,
    val friction: Float = 0.4f,
    val restitution: Float = 0.2f,
    val linearDamping: Float = 0.06f,
    val angularDamping: Float = 0.08f,
    val isSensor: Boolean = false,
    val removeOnFloorContact: Boolean = false,
    val explodeOnFirstImpact: Boolean = false,
    val explodeOnImpactByIds: Set<PhysicsId> = emptySet(),
    val explosionSpec: ExplosionSpec = ExplosionSpec(),
)

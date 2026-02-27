package dev.damianpetla.physicsscene.api

enum class PhysicsBodyType {
    Static,
    Dynamic,
    Kinematic,
}

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

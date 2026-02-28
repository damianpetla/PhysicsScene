package dev.damianpetla.physicsscene.api

/**
 * Runtime lifecycle state of a body in [dev.damianpetla.physicsscene.PhysicsScene].
 */
enum class PhysicsLifecycleState {
    /**
     * Registered and waiting for activation.
     */
    Idle,

    /**
     * Active in dynamic simulation.
     */
    Falling,

    /**
     * In shattering phase (shard creation in progress).
     */
    Shattering,

    /**
     * Removed from world.
     */
    Removed,
}

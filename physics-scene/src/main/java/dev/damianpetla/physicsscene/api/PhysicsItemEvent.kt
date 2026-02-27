package dev.damianpetla.physicsscene.api

typealias PhysicsId = String

enum class PhysicsItemEventType {
    Activated,
    ShatteringStarted,
    ShardHit,
    ShardDropped,
    Removed,
}

data class PhysicsItemEvent(
    val id: PhysicsId,
    val type: PhysicsItemEventType,
)

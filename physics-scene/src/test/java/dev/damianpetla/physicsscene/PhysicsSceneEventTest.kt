package dev.damianpetla.physicsscene

import dev.damianpetla.physicsscene.api.ShardDropped
import dev.damianpetla.physicsscene.api.ShardHit
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsSceneEventTest {

    @Test
    fun shardHit_containsOwnerHitterAndShardId() {
        val event = ShardHit(
            ownerId = "owner",
            hitterId = "hitter",
            shardId = 42L,
        )

        assertEquals("owner", event.ownerId)
        assertEquals("hitter", event.hitterId)
        assertEquals(42L, event.shardId)
    }

    @Test
    fun shardDropped_containsOwnerAndShardId() {
        val event = ShardDropped(
            ownerId = "owner",
            shardId = 7L,
        )

        assertEquals("owner", event.ownerId)
        assertEquals(7L, event.shardId)
    }
}

package dev.damianpetla.physicsscene

import dev.damianpetla.physicsscene.api.CustomEffect
import dev.damianpetla.physicsscene.api.CenterBurstEffect
import dev.damianpetla.physicsscene.api.FallingShatterEffect
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import dev.damianpetla.physicsscene.api.ShardColliderShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEffectTest {

    @Test
    fun fallingShatterEffect_buildsShatterEnabledBodySpec() {
        val effect = FallingShatterEffect(
            shardsRows = 0,
            shardsCols = 3,
            impulseMin = 0.14f,
            impulseMax = 0.08f,
        )

        val spec = effect.bodySpec()

        assertEquals(PhysicsBodyType.Static, spec.bodyType)
        assertTrue(spec.explodeOnFirstImpact)
        assertEquals(1, spec.explosionSpec.shardsRows)
        assertEquals(3, spec.explosionSpec.shardsCols)
        assertEquals(0.14f, spec.explosionSpec.impulseMin, 0.0001f)
        assertEquals(0.14f, spec.explosionSpec.impulseMax, 0.0001f)
    }

    @Test
    fun customEffect_allowsLowLevelSpecControl() {
        val effect = CustomEffect { base ->
            base.copy(
                bodyType = PhysicsBodyType.Dynamic,
                friction = 0.9f,
                explodeOnFirstImpact = false,
            )
        }

        val spec = effect.bodySpec(
            baseSpec = PhysicsBodySpec(
                bodyType = PhysicsBodyType.Static,
                friction = 0.1f,
                explodeOnFirstImpact = true,
            ),
        )

        assertEquals(PhysicsBodyType.Dynamic, spec.bodyType)
        assertEquals(0.9f, spec.friction, 0.0001f)
        assertEquals(false, spec.explodeOnFirstImpact)
    }

    @Test
    fun centerBurstEffect_buildsExplicitClickBurstWithCircleShards() {
        val spec = CenterBurstEffect().bodySpec()

        assertEquals(PhysicsBodyType.Static, spec.bodyType)
        assertEquals(false, spec.explodeOnFirstImpact)
        assertEquals(ShardColliderShape.Circle, spec.explosionSpec.shardColliderShape)
    }
}

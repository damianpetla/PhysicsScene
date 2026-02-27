package dev.damianpetla.physicsscene

import com.badlogic.gdx.box2d.enums.b2BodyType
import dev.damianpetla.physicsscene.engine.toBodyDefConfig
import dev.damianpetla.physicsscene.engine.toBox2dBodyType
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsSpecMappingTest {

    @Test
    fun toBodyDefConfig_mapsDynamicTypeAndDamping() {
        val spec = PhysicsBodySpec(
            bodyType = PhysicsBodyType.Dynamic,
            linearDamping = 0.3f,
            angularDamping = 0.7f,
        )

        val config = spec.toBodyDefConfig()

        assertEquals(PhysicsBodyType.Dynamic, config.bodyType)
        assertEquals(0.3f, config.linearDamping, 0.0001f)
        assertEquals(0.7f, config.angularDamping, 0.0001f)
    }

    @Test
    fun toBox2dBodyType_mapsAllBodyTypes() {
        assertEquals(b2BodyType.b2_staticBody, PhysicsBodyType.Static.toBox2dBodyType())
        assertEquals(b2BodyType.b2_dynamicBody, PhysicsBodyType.Dynamic.toBox2dBodyType())
        assertEquals(b2BodyType.b2_kinematicBody, PhysicsBodyType.Kinematic.toBox2dBodyType())
    }
}

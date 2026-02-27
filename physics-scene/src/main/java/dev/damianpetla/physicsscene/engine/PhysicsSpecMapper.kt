package dev.damianpetla.physicsscene.engine

import com.badlogic.gdx.box2d.Box2d
import com.badlogic.gdx.box2d.enums.b2BodyType
import com.badlogic.gdx.box2d.structs.b2BodyDef
import com.badlogic.gdx.box2d.structs.b2ShapeDef
import com.badlogic.gdx.box2d.structs.b2SurfaceMaterial
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType

internal data class BodyDefConfig(
    val bodyType: PhysicsBodyType,
    val linearDamping: Float,
    val angularDamping: Float,
)

internal fun PhysicsBodyType.toBox2dBodyType(): b2BodyType {
    return when (this) {
        PhysicsBodyType.Static -> b2BodyType.b2_staticBody
        PhysicsBodyType.Dynamic -> b2BodyType.b2_dynamicBody
        PhysicsBodyType.Kinematic -> b2BodyType.b2_kinematicBody
    }
}

internal fun PhysicsBodySpec.toBodyDefConfig(
    bodyTypeOverride: PhysicsBodyType = bodyType,
): BodyDefConfig {
    return BodyDefConfig(
        bodyType = bodyTypeOverride,
        linearDamping = linearDamping,
        angularDamping = angularDamping,
    )
}

internal fun PhysicsBodySpec.buildBodyDef(
    bodyTypeOverride: PhysicsBodyType = bodyType,
): b2BodyDef {
    val config = toBodyDefConfig(bodyTypeOverride)
    val bodyDef = runCatching { Box2d.b2DefaultBodyDef() }
        .getOrElse { b2BodyDef() }

    bodyDef.type(config.bodyType.toBox2dBodyType())
    bodyDef.linearDamping(config.linearDamping)
    bodyDef.angularDamping(config.angularDamping)
    return bodyDef
}

internal fun PhysicsBodySpec.buildShapeDef(
    enableContactEvents: Boolean,
): b2ShapeDef {
    val shapeDef = runCatching { Box2d.b2DefaultShapeDef() }
        .getOrElse { b2ShapeDef() }

    shapeDef.density(this.density)
    shapeDef.isSensor(this.isSensor)
    shapeDef.enableContactEvents(enableContactEvents)
    shapeDef.enableSensorEvents(this.isSensor && enableContactEvents)

    val material = b2SurfaceMaterial().apply {
        friction(this@buildShapeDef.friction)
        restitution(this@buildShapeDef.restitution)
    }
    shapeDef.setMaterial(material)

    return shapeDef
}

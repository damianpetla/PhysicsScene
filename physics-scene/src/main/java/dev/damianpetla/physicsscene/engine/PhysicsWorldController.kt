package dev.damianpetla.physicsscene.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import android.util.Log
import com.badlogic.gdx.box2d.Box2d
import com.badlogic.gdx.box2d.enums.b2BodyType
import com.badlogic.gdx.box2d.structs.b2BodyId
import com.badlogic.gdx.box2d.structs.b2Circle
import com.badlogic.gdx.box2d.structs.b2ShapeId
import com.badlogic.gdx.box2d.structs.b2SurfaceMaterial
import com.badlogic.gdx.box2d.structs.b2Vec2
import com.badlogic.gdx.box2d.structs.b2WorldId
import dev.damianpetla.physicsscene.api.ExplosionSpec
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import dev.damianpetla.physicsscene.api.PhysicsCollisionIds
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.ShardColliderShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal data class LayoutPhysicsNode(
    val id: PhysicsId,
    val spec: PhysicsBodySpec,
    val sizePx: IntSize,
    val initialCenterPx: Offset,
    val initialRotationDegrees: Float,
)

internal data class PhysicsNodeSnapshot(
    val centerPx: Offset,
    val angleRad: Float,
)

internal data class PhysicsShardSnapshot(
    val id: Long,
    val ownerId: PhysicsId,
    val atlas: ImageBitmap,
    val srcRect: IntRect,
    val centerPx: Offset,
    val sizePx: IntSize,
    val angleRad: Float,
    val alpha: Float,
    val colliderShape: ShardColliderShape,
)

private data class ItemBodyEntry(
    val id: PhysicsId,
    var spec: PhysicsBodySpec,
    var sizePx: IntSize,
    var bodyId: b2BodyId,
    var shapeId: b2ShapeId,
    var activated: Boolean,
)

private data class ShardBodyEntry(
    val id: Long,
    val ownerId: PhysicsId,
    val atlas: ImageBitmap,
    val srcRect: IntRect,
    val sizePx: IntSize,
    val bodyId: b2BodyId,
    val shapeId: b2ShapeId,
    val colliderShape: ShardColliderShape,
    val bornAtSeconds: Float,
    val expireAtSeconds: Float?,
)

internal class PhysicsWorldController(
    private val pixelsPerMeter: Float,
    gravityPxPerSecondSq: Offset,
    private val onItemShouldExplode: (PhysicsId) -> Unit,
    private val onItemShouldRemove: (PhysicsId) -> Unit,
    private val onShardHit: (ownerId: PhysicsId, hitterId: PhysicsId, shardId: Long) -> Unit,
    private val onShardDropped: (ownerId: PhysicsId, shardId: Long) -> Unit,
) {
    private val logTag = "PhysicsWorld"
    private val worldId: b2WorldId = createWorld(gravityPxPerSecondSq)
    private val items = LinkedHashMap<PhysicsId, ItemBodyEntry>()
    private val shards = LinkedHashMap<Long, ShardBodyEntry>()
    private val removedIds = LinkedHashSet<PhysicsId>()
    private val firstImpactReported = LinkedHashSet<PhysicsId>()
    private val outOfBoundsReported = LinkedHashSet<PhysicsId>()
    private val itemOwnerByBodyKey = HashMap<Long, PhysicsId>()
    private val shardOwnerByBodyKey = HashMap<Long, Long>()
    private val random = Random(System.nanoTime())

    private var worldWidthPx: Int = 0
    private var worldHeightPx: Int = 0
    private var worldBoundsBodyId: b2BodyId? = null
    private var nextShardId: Long = 1L
    private var simulationTimeSeconds: Float = 0f
    private var debugSampleAccumulatorSeconds: Float = 0f

    companion object {
        private const val WORLD_SOLVER_SUB_STEPS = 4
        private const val ITEM_REMOVE_AT_FLOOR_MARGIN_PX = 20f
        private const val SHARD_REMOVE_AT_FLOOR_MARGIN_PX = 10f
        private const val DEBUG_SAMPLE_INTERVAL_SECONDS = 0.33f
    }

    fun syncLayout(
        worldSizePx: IntSize,
        nodes: List<LayoutPhysicsNode>,
    ) {
        if (worldSizePx.width > 0 &&
            worldSizePx.height > 0 &&
            (worldSizePx.width != worldWidthPx || worldSizePx.height != worldHeightPx)
        ) {
            worldWidthPx = worldSizePx.width
            worldHeightPx = worldSizePx.height
            recreateWorldBounds()
        }

        val visibleIds = nodes.mapTo(mutableSetOf()) { it.id }
        val removedFromLayout = items.keys.filterNot { it in visibleIds }
        if (removedFromLayout.isNotEmpty()) {
            Log.d(logTag, "syncLayout removedFromLayout=$removedFromLayout")
        }
        removedFromLayout.forEach { remove(it) }

        nodes.forEach { node ->
            if (node.id in removedIds) return@forEach
            val existing = items[node.id]
            if (existing == null) {
                items[node.id] = createItemEntry(node)
            } else {
                existing.spec = node.spec

                // Node bounds may be reported before full registration/spec update.
                // If a body was created as static first, align its type with the spec
                // until it becomes explicitly activated by commands.
                if (!existing.activated) {
                    val desiredType = existing.spec.bodyType.toBox2dBodyType()
                    val currentType = Box2d.b2Body_GetType(existing.bodyId)
                    if (currentType != desiredType) {
                        Box2d.b2Body_SetType(existing.bodyId, desiredType)
                        if (desiredType == b2BodyType.b2_dynamicBody) {
                            Box2d.b2Body_SetAwake(existing.bodyId, true)
                            existing.activated = true
                        }
                    }
                }

                if (existing.sizePx != node.sizePx) {
                    val replacement = recreateBodyKeepingState(entry = existing, newSizePx = node.sizePx)
                    existing.sizePx = node.sizePx
                    existing.bodyId = replacement.first
                    existing.shapeId = replacement.second
                }
                if (!existing.activated && existing.spec.bodyType != PhysicsBodyType.Dynamic) {
                    Box2d.b2Body_SetTransform(
                        existing.bodyId,
                        vec2(
                            x = pxToMeters(node.initialCenterPx.x),
                            y = pxToMeters(node.initialCenterPx.y),
                        ),
                        rotFromDegrees(node.initialRotationDegrees),
                    )
                }
            }
        }
    }

    fun activate(id: PhysicsId): Boolean {
        val item = items[id] ?: run {
            Log.d(logTag, "activate failed: missing item id=$id")
            return false
        }
        if (!Box2d.b2Body_IsValid(item.bodyId)) {
            Log.d(logTag, "activate failed: invalid body id=$id")
            return false
        }
        val currentType = Box2d.b2Body_GetType(item.bodyId)
        if (currentType != b2BodyType.b2_dynamicBody) {
            val replacement = recreateBodyKeepingState(
                entry = item,
                newSizePx = item.sizePx,
                forcedBodyType = b2BodyType.b2_dynamicBody,
            )
            item.bodyId = replacement.first
            item.shapeId = replacement.second
        }
        Box2d.b2Body_Enable(item.bodyId)
        Box2d.b2Body_SetGravityScale(item.bodyId, 1f)
        Box2d.b2Body_ApplyMassFromShapes(item.bodyId)
        Box2d.b2Body_SetAwake(item.bodyId, true)
        item.activated = true
        val position = Box2d.b2Body_GetPosition(item.bodyId)
        val velocity = Box2d.b2Body_GetLinearVelocity(item.bodyId)
        Log.d(
            logTag,
            "activate success id=$id type=${Box2d.b2Body_GetType(item.bodyId)} " +
                "awake=${Box2d.b2Body_IsAwake(item.bodyId)} " +
                "enabled=${Box2d.b2Body_IsEnabled(item.bodyId)} " +
                "gravityScale=${Box2d.b2Body_GetGravityScale(item.bodyId)} " +
                "mass=${Box2d.b2Body_GetMass(item.bodyId)} " +
                "pos=(${position.x()}, ${position.y()}) vel=(${velocity.x()}, ${velocity.y()})",
        )
        return true
    }

    fun remove(id: PhysicsId) {
        removedIds += id
        firstImpactReported -= id
        outOfBoundsReported -= id
        items.remove(id)?.also { destroyItemEntry(it) }
        removeShardsForOwner(id)
    }

    fun respawn(id: PhysicsId): Boolean {
        val wasRemoved = removedIds.remove(id)
        firstImpactReported -= id
        outOfBoundsReported -= id
        removeShardsForOwner(id)
        return wasRemoved
    }

    fun removeShardsForOwner(id: PhysicsId) {
        val shardIds = shards.values
            .filter { it.ownerId == id }
            .map { it.id }

        shardIds.forEach { shardId ->
            shards.remove(shardId)?.also { destroyShardEntry(it) }
        }
    }

    fun bodySpecFor(id: PhysicsId): PhysicsBodySpec? {
        return items[id]?.spec
    }

    fun bodySnapshot(id: PhysicsId): PhysicsNodeSnapshot? {
        val entry = items[id] ?: return null
        return PhysicsNodeSnapshot(
            centerPx = bodyCenterPx(entry.bodyId),
            angleRad = bodyAngleRad(entry.bodyId),
        )
    }

    fun allBodySnapshots(): Map<PhysicsId, PhysicsNodeSnapshot> {
        return items.mapValues { (_, entry) ->
            PhysicsNodeSnapshot(
                centerPx = bodyCenterPx(entry.bodyId),
                angleRad = bodyAngleRad(entry.bodyId),
            )
        }
    }

    fun allShardSnapshots(): List<PhysicsShardSnapshot> {
        val now = simulationTimeSeconds
        return shards.values.map { shard ->
            val alpha = if (shard.expireAtSeconds == null) {
                1f
            } else {
                val lifetime = (shard.expireAtSeconds - shard.bornAtSeconds).coerceAtLeast(0.0001f)
                ((shard.expireAtSeconds - now) / lifetime).coerceIn(0f, 1f)
            }
            PhysicsShardSnapshot(
                id = shard.id,
                ownerId = shard.ownerId,
                atlas = shard.atlas,
                srcRect = shard.srcRect,
                centerPx = bodyCenterPx(shard.bodyId),
                sizePx = shard.sizePx,
                angleRad = bodyAngleRad(shard.bodyId),
                alpha = alpha,
                colliderShape = shard.colliderShape,
            )
        }
    }

    fun step(stepSeconds: Float) {
        simulationTimeSeconds += stepSeconds
        Box2d.b2World_Step(worldId, stepSeconds, WORLD_SOLVER_SUB_STEPS)
        debugSampleAccumulatorSeconds += stepSeconds
        if (debugSampleAccumulatorSeconds >= DEBUG_SAMPLE_INTERVAL_SECONDS) {
            debugSampleAccumulatorSeconds = 0f
            logActivatedBodiesSample()
        }
        detectOutOfBoundsItems()
        consumeContactEvents()
        cleanupFinishedShards()
    }

    fun applyRadialImpulseFromBody(
        originId: PhysicsId,
        radiusPx: Float,
        impulse: Float,
    ): Boolean {
        val originBody = items[originId]?.bodyId ?: return false
        if (!Box2d.b2Body_IsValid(originBody)) return false

        val centerPx = bodyCenterPx(originBody)
        applyRadialImpulse(
            centerPx = centerPx,
            radiusPx = radiusPx,
            impulse = impulse,
            excludedBodyKey = bodyKey(originBody),
        )
        return true
    }

    fun applyLinearImpulse(
        id: PhysicsId,
        impulsePx: Offset,
    ): Boolean {
        val item = items[id] ?: return false
        if (!Box2d.b2Body_IsValid(item.bodyId)) return false

        if (!item.activated || Box2d.b2Body_GetType(item.bodyId) != b2BodyType.b2_dynamicBody) {
            val replacement = recreateBodyKeepingState(
                entry = item,
                newSizePx = item.sizePx,
                forcedBodyType = b2BodyType.b2_dynamicBody,
            )
            item.bodyId = replacement.first
            item.shapeId = replacement.second
            item.activated = true
        }

        val impulseX = impulsePx.x / pixelsPerMeter
        val impulseY = impulsePx.y / pixelsPerMeter
        if (abs(impulseX) <= 0.0001f && abs(impulseY) <= 0.0001f) return false

        Box2d.b2Body_SetAwake(item.bodyId, true)
        Box2d.b2Body_ApplyLinearImpulseToCenter(
            item.bodyId,
            vec2(
                x = impulseX,
                y = impulseY,
            ),
            true,
        )
        return true
    }

    fun attractShards(
        ownerId: PhysicsId,
        targetPx: Offset,
        impulsePx: Float,
        maxDistancePx: Float,
    ): Int {
        if (impulsePx <= 0f || maxDistancePx <= 0f) return 0
        var affected = 0
        val impulseMeters = impulsePx / pixelsPerMeter

        shards.values.forEach { shard ->
            if (shard.ownerId != ownerId) return@forEach
            if (!Box2d.b2Body_IsValid(shard.bodyId)) return@forEach
            if (Box2d.b2Body_GetType(shard.bodyId) != b2BodyType.b2_dynamicBody) return@forEach

            val centerPx = bodyCenterPx(shard.bodyId)
            val dx = targetPx.x - centerPx.x
            val dy = targetPx.y - centerPx.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (distance <= 0.001f || distance > maxDistancePx) return@forEach

            val dirX = dx / distance
            val dirY = dy / distance
            val falloff = (1f - (distance / maxDistancePx)).coerceIn(0f, 1f)
            val scaled = impulseMeters * (0.15f + falloff * falloff)
            if (scaled <= 0.0001f) return@forEach

            Box2d.b2Body_SetAwake(shard.bodyId, true)
            Box2d.b2Body_ApplyLinearImpulseToCenter(
                shard.bodyId,
                vec2(
                    x = dirX * scaled,
                    y = dirY * scaled,
                ),
                true,
            )
            affected += 1
        }

        return affected
    }

    fun removeShard(shardId: Long): Boolean {
        val entry = shards.remove(shardId) ?: return false
        destroyShardEntry(entry)
        return true
    }

    fun resetScene() {
        shards.values.toList().forEach { destroyShardEntry(it) }
        items.values.toList().forEach { destroyItemEntry(it) }
        shards.clear()
        items.clear()
        removedIds.clear()
        firstImpactReported.clear()
        outOfBoundsReported.clear()
        itemOwnerByBodyKey.clear()
        shardOwnerByBodyKey.clear()
        nextShardId = 1L
        simulationTimeSeconds = 0f
    }

    fun shatter(
        id: PhysicsId,
        atlas: ImageBitmap,
        explosionSpec: ExplosionSpec,
    ): Boolean {
        if (!explosionSpec.enabled) {
            remove(id)
            return false
        }

        val item = items[id] ?: return false
        val bodyId = item.bodyId

        val centerPx = bodyCenterPx(bodyId)
        val angleRad = bodyAngleRad(bodyId)
        val baseVelocity = Box2d.b2Body_GetLinearVelocity(bodyId)
        val baseAngularVelocity = Box2d.b2Body_GetAngularVelocity(bodyId)
        val ttlSeconds = if (explosionSpec.shardTtlMs > 0) explosionSpec.shardTtlMs / 1_000f else null

        val rows = explosionSpec.shardsRows.coerceAtLeast(1)
        val cols = resolveShardCols(
            atlasWidth = atlas.width,
            atlasHeight = atlas.height,
            rows = rows,
            requestedCols = explosionSpec.shardsCols,
            squareShards = explosionSpec.squareShards,
        )
        val slices = sliceGrid(
            atlasWidth = atlas.width,
            atlasHeight = atlas.height,
            rows = rows,
            cols = cols,
        )

        slices.forEach { slice ->
            val localX = slice.centerOffsetPx.x - atlas.width / 2f
            val localY = slice.centerOffsetPx.y - atlas.height / 2f
            val rotatedOffset = rotate(localX, localY, angleRad)
            val shardCenterPx = Offset(
                x = centerPx.x + rotatedOffset.first,
                y = centerPx.y + rotatedOffset.second,
            )

            val shardBodyDef = item.spec.buildBodyDef(bodyTypeOverride = PhysicsBodyType.Dynamic).apply {
                setPosition(
                    vec2(
                        x = pxToMeters(shardCenterPx.x),
                        y = pxToMeters(shardCenterPx.y),
                    ),
                )
                setRotation(rotFromRadians(angleRad))
            }

            val shardBodyId = Box2d.b2CreateBody(worldId, shardBodyDef.asPointer())
            val shardSpec = item.spec.copy(
                density = (item.spec.density * 0.4f).coerceAtLeast(0.02f),
                friction = (item.spec.friction * 0.8f).coerceAtLeast(0.05f),
                restitution = (item.spec.restitution + 0.1f).coerceAtMost(0.95f),
            )
            val shardShapeId = when (explosionSpec.shardColliderShape) {
                ShardColliderShape.Box -> createBoxShape(
                    bodyId = shardBodyId,
                    sizePx = slice.sizePx,
                    spec = shardSpec,
                    enableContactEvents = true,
                )

                ShardColliderShape.Circle -> createCircleShape(
                    bodyId = shardBodyId,
                    sizePx = slice.sizePx,
                    spec = shardSpec,
                    enableContactEvents = true,
                )
            }

            Box2d.b2Body_SetLinearVelocity(
                shardBodyId,
                vec2(x = baseVelocity.x(), y = baseVelocity.y()),
            )
            Box2d.b2Body_SetAngularVelocity(
                shardBodyId,
                baseAngularVelocity + random.nextFloat() * 1.4f - 0.7f,
            )

            var dirX = localX
            var dirY = localY
            if (abs(dirX) < 0.001f && abs(dirY) < 0.001f) {
                dirX = random.nextFloat() - 0.5f
                dirY = random.nextFloat() - 0.5f
            }
            val length = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.0001f)
            dirX /= length
            dirY /= length

            val jitteredDirection = rotate(
                x = dirX,
                y = dirY,
                angleRad = angleRad + (random.nextFloat() - 0.5f) * 0.44f,
            )

            val impulse = random.nextFloat()
                .let { explosionSpec.impulseMin + (explosionSpec.impulseMax - explosionSpec.impulseMin) * it }
                .coerceAtLeast(0.05f)

            Box2d.b2Body_ApplyLinearImpulseToCenter(
                shardBodyId,
                vec2(
                    x = jitteredDirection.first * impulse,
                    y = jitteredDirection.second * impulse,
                ),
                true,
            )

            val shardId = nextShardId++
            shards[shardId] = ShardBodyEntry(
                id = shardId,
                ownerId = id,
                atlas = atlas,
                srcRect = slice.srcRect,
                sizePx = slice.sizePx,
                bodyId = shardBodyId,
                shapeId = shardShapeId,
                colliderShape = explosionSpec.shardColliderShape,
                bornAtSeconds = simulationTimeSeconds,
                expireAtSeconds = ttlSeconds?.let { simulationTimeSeconds + it },
            )
            trackShardBody(shardId, shardBodyId)
        }

        destroyItemEntry(item)
        items.remove(id)
        firstImpactReported -= id
        removedIds += id
        return true
    }

    fun dispose() {
        shards.values.toList().forEach { destroyShardEntry(it) }
        items.values.toList().forEach { destroyItemEntry(it) }
        shards.clear()
        items.clear()
        itemOwnerByBodyKey.clear()
        shardOwnerByBodyKey.clear()
        outOfBoundsReported.clear()

        worldBoundsBodyId?.let { safeDestroyBody(it) }
        worldBoundsBodyId = null

        if (Box2d.b2World_IsValid(worldId)) {
            Box2d.b2DestroyWorld(worldId)
        }
    }

    private fun createWorld(gravityPxPerSecondSq: Offset): b2WorldId {
        val worldDef = Box2d.b2DefaultWorldDef().apply {
            setGravity(
                vec2(
                    x = gravityPxPerSecondSq.x / pixelsPerMeter,
                    y = gravityPxPerSecondSq.y / pixelsPerMeter,
                ),
            )
        }
        return Box2d.b2CreateWorld(worldDef.asPointer())
    }

    private fun createItemEntry(node: LayoutPhysicsNode): ItemBodyEntry {
        val bodyDef = node.spec.buildBodyDef().apply {
            setPosition(
                vec2(
                    x = pxToMeters(node.initialCenterPx.x),
                    y = pxToMeters(node.initialCenterPx.y),
                ),
            )
            setRotation(rotFromDegrees(node.initialRotationDegrees))
        }

        val bodyId = Box2d.b2CreateBody(worldId, bodyDef.asPointer())
        val shapeId = createBoxShape(
            bodyId = bodyId,
            sizePx = node.sizePx,
            spec = node.spec,
            enableContactEvents = true,
        )
        Box2d.b2Body_Enable(bodyId)
        Box2d.b2Body_SetGravityScale(bodyId, 1f)
        Box2d.b2Body_ApplyMassFromShapes(bodyId)

        val activated = node.spec.bodyType == PhysicsBodyType.Dynamic
        trackItemBody(node.id, bodyId)
        Log.d(
            logTag,
            "createItemEntry id=${node.id} type=${Box2d.b2Body_GetType(bodyId)} " +
                "size=${node.sizePx.width}x${node.sizePx.height} activated=$activated",
        )

        return ItemBodyEntry(
            id = node.id,
            spec = node.spec,
            sizePx = node.sizePx,
            bodyId = bodyId,
            shapeId = shapeId,
            activated = activated,
        )
    }

    private fun recreateBodyKeepingState(
        entry: ItemBodyEntry,
        newSizePx: IntSize,
        forcedBodyType: b2BodyType? = null,
    ): Pair<b2BodyId, b2ShapeId> {
        val oldBodyId = entry.bodyId
        val oldType = forcedBodyType ?: Box2d.b2Body_GetType(oldBodyId)
        val oldPosition = Box2d.b2Body_GetPosition(oldBodyId)
        val oldRotation = Box2d.b2Body_GetRotation(oldBodyId)
        val oldLinearVelocity = Box2d.b2Body_GetLinearVelocity(oldBodyId)
        val oldAngularVelocity = Box2d.b2Body_GetAngularVelocity(oldBodyId)

        val bodyDef = entry.spec.buildBodyDef().apply {
            type(oldType)
            setPosition(vec2(x = oldPosition.x(), y = oldPosition.y()))
            setRotation(oldRotation)
            setLinearVelocity(vec2(x = oldLinearVelocity.x(), y = oldLinearVelocity.y()))
            angularVelocity(oldAngularVelocity)
        }

        val replacementBodyId = Box2d.b2CreateBody(worldId, bodyDef.asPointer())
        val replacementShapeId = createBoxShape(
            bodyId = replacementBodyId,
            sizePx = newSizePx,
            spec = entry.spec,
            enableContactEvents = true,
        )

        untrackItemBody(oldBodyId)
        safeDestroyBody(oldBodyId)
        trackItemBody(entry.id, replacementBodyId)

        return replacementBodyId to replacementShapeId
    }

    private fun recreateWorldBounds() {
        worldBoundsBodyId?.let { safeDestroyBody(it) }

        val bodyDef = Box2d.b2DefaultBodyDef().apply {
            type(b2BodyType.b2_staticBody)
        }
        val bodyId = Box2d.b2CreateBody(worldId, bodyDef.asPointer())

        val thicknessPx = 10f
        createWallFixture(
            bodyId = bodyId,
            centerXPx = worldWidthPx / 2f,
            centerYPx = thicknessPx / 2f,
            halfWidthPx = worldWidthPx / 2f,
            halfHeightPx = thicknessPx / 2f,
        )
        createWallFixture(
            bodyId = bodyId,
            centerXPx = worldWidthPx / 2f,
            centerYPx = worldHeightPx - thicknessPx / 2f,
            halfWidthPx = worldWidthPx / 2f,
            halfHeightPx = thicknessPx / 2f,
        )
        createWallFixture(
            bodyId = bodyId,
            centerXPx = thicknessPx / 2f,
            centerYPx = worldHeightPx / 2f,
            halfWidthPx = thicknessPx / 2f,
            halfHeightPx = worldHeightPx / 2f,
        )
        createWallFixture(
            bodyId = bodyId,
            centerXPx = worldWidthPx - thicknessPx / 2f,
            centerYPx = worldHeightPx / 2f,
            halfWidthPx = thicknessPx / 2f,
            halfHeightPx = worldHeightPx / 2f,
        )

        worldBoundsBodyId = bodyId
    }

    private fun createWallFixture(
        bodyId: b2BodyId,
        centerXPx: Float,
        centerYPx: Float,
        halfWidthPx: Float,
        halfHeightPx: Float,
    ) {
        val shapeDef = Box2d.b2DefaultShapeDef().apply {
            density(0f)
            isSensor(false)
            enableContactEvents(true)
            enableSensorEvents(false)
            val material = b2SurfaceMaterial().apply {
                friction(0.15f)
                restitution(0.2f)
            }
            setMaterial(material)
        }

        val polygon = Box2d.b2MakeOffsetBox(
            pxToMeters(halfWidthPx),
            pxToMeters(halfHeightPx),
            vec2(
                x = pxToMeters(centerXPx),
                y = pxToMeters(centerYPx),
            ),
            rotFromRadians(0f),
        )

        Box2d.b2CreatePolygonShape(bodyId, shapeDef.asPointer(), polygon.asPointer())
    }

    private fun createBoxShape(
        bodyId: b2BodyId,
        sizePx: IntSize,
        spec: PhysicsBodySpec,
        enableContactEvents: Boolean,
    ): b2ShapeId {
        val shapeDef = spec.buildShapeDef(enableContactEvents = enableContactEvents)
        val polygon = Box2d.b2MakeBox(
            pxToMeters(sizePx.width / 2f),
            pxToMeters(sizePx.height / 2f),
        )
        return Box2d.b2CreatePolygonShape(bodyId, shapeDef.asPointer(), polygon.asPointer())
    }

    private fun createCircleShape(
        bodyId: b2BodyId,
        sizePx: IntSize,
        spec: PhysicsBodySpec,
        enableContactEvents: Boolean,
    ): b2ShapeId {
        val shapeDef = spec.buildShapeDef(enableContactEvents = enableContactEvents)
        val radiusMeters = pxToMeters(minOf(sizePx.width, sizePx.height) / 2f).coerceAtLeast(0.001f)
        val circle = b2Circle().apply {
            center().apply {
                x(0f)
                y(0f)
            }
            radius(radiusMeters)
        }
        return Box2d.b2CreateCircleShape(bodyId, shapeDef.asPointer(), circle.asPointer())
    }

    private fun consumeContactEvents() {
        val contactEvents = Box2d.b2World_GetContactEvents(worldId)
        val beginCount = contactEvents.beginCount()
        if (beginCount <= 0) return

        val beginEvents = contactEvents.beginEvents()
        val shardHitByItem = LinkedHashMap<Long, PhysicsId>()
        for (index in 0 until beginCount) {
            val event = beginEvents.get(index)
            val shapeA = event.getShapeIdA()
            val shapeB = event.getShapeIdB()
            val itemA = itemIdForShape(shapeA)
            val itemB = itemIdForShape(shapeB)
            val shardA = shardIdForShape(shapeA)
            val shardB = shardIdForShape(shapeB)
            val worldA = isWorldBoundsShape(shapeA)
            val worldB = isWorldBoundsShape(shapeB)

            // Explosions can be triggered by:
            // 1) collisions between tracked item bodies (at least one side dynamic),
            // 2) dynamic item collisions with world bounds.
            if (itemA != null && itemB != null && itemA != itemB) {
                val dynamicCollision = isDynamicItem(itemA) || isDynamicItem(itemB)
                if (dynamicCollision) {
                    maybeTriggerImpactExplosion(id = itemA, hitById = itemB)
                    maybeTriggerImpactExplosion(id = itemB, hitById = itemA)
                }
            }
            if (itemA != null && worldB && isDynamicItem(itemA)) {
                maybeTriggerImpactExplosion(
                    id = itemA,
                    hitById = PhysicsCollisionIds.WORLD_BOUNDS,
                )
            }
            if (itemB != null && worldA && isDynamicItem(itemB)) {
                maybeTriggerImpactExplosion(
                    id = itemB,
                    hitById = PhysicsCollisionIds.WORLD_BOUNDS,
                )
            }

            // Dynamic item hitting a shard removes that shard immediately.
            if (itemA != null && shardB != null && isDynamicItem(itemA)) {
                if (shardB !in shardHitByItem) {
                    shardHitByItem[shardB] = itemA
                }
            }
            if (itemB != null && shardA != null && isDynamicItem(itemB)) {
                if (shardA !in shardHitByItem) {
                    shardHitByItem[shardA] = itemB
                }
            }
        }
        shardHitByItem.forEach { (shardId, hitterId) ->
            shards[shardId]?.let { shard ->
                onShardHit(shard.ownerId, hitterId, shard.id)
            }
            shards.remove(shardId)?.also { destroyShardEntry(it) }
        }
    }

    private fun itemIdForShape(shapeId: b2ShapeId): PhysicsId? {
        if (!Box2d.b2Shape_IsValid(shapeId)) {
            return null
        }
        val bodyId = Box2d.b2Shape_GetBody(shapeId)
        return itemOwnerByBodyKey[bodyKey(bodyId)]
    }

    private fun shardIdForShape(shapeId: b2ShapeId): Long? {
        if (!Box2d.b2Shape_IsValid(shapeId)) {
            return null
        }
        val bodyId = Box2d.b2Shape_GetBody(shapeId)
        return shardOwnerByBodyKey[bodyKey(bodyId)]
    }

    private fun isWorldBoundsShape(shapeId: b2ShapeId): Boolean {
        if (!Box2d.b2Shape_IsValid(shapeId)) return false
        val boundsBodyId = worldBoundsBodyId ?: return false
        if (!Box2d.b2Body_IsValid(boundsBodyId)) return false
        val shapeBodyId = Box2d.b2Shape_GetBody(shapeId)
        return bodyKey(shapeBodyId) == bodyKey(boundsBodyId)
    }

    private fun isDynamicItem(id: PhysicsId): Boolean {
        val bodyId = items[id]?.bodyId ?: return false
        if (!Box2d.b2Body_IsValid(bodyId)) return false
        return Box2d.b2Body_GetType(bodyId) == b2BodyType.b2_dynamicBody
    }

    private fun cleanupFinishedShards() {
        val toRemove = ArrayList<Long>()
        shards.values.forEach { shard ->
            val centerPx = bodyCenterPx(shard.bodyId)
            val bottomPx = centerPx.y + shard.sizePx.height / 2f
            if (worldHeightPx > 0 && bottomPx >= worldHeightPx - SHARD_REMOVE_AT_FLOOR_MARGIN_PX) {
                onShardDropped(shard.ownerId, shard.id)
                toRemove += shard.id
                return@forEach
            }

            val ttl = shard.expireAtSeconds
            if (ttl != null) {
                if (simulationTimeSeconds >= ttl) {
                    toRemove += shard.id
                }
                return@forEach
            }
        }

        toRemove.forEach { shardId ->
            shards.remove(shardId)?.also { destroyShardEntry(it) }
        }
    }

    private fun detectOutOfBoundsItems() {
        if (worldHeightPx <= 0) return

        items.values.forEach { item ->
            if (!item.spec.removeOnFloorContact) return@forEach
            if (!item.activated) return@forEach
            if (!Box2d.b2Body_IsValid(item.bodyId)) return@forEach
            if (Box2d.b2Body_GetType(item.bodyId) != b2BodyType.b2_dynamicBody) return@forEach
            if (item.id in outOfBoundsReported) return@forEach

            val centerPx = bodyCenterPx(item.bodyId)
            val bottomPx = centerPx.y + item.sizePx.height / 2f
            if (bottomPx >= worldHeightPx - ITEM_REMOVE_AT_FLOOR_MARGIN_PX) {
                outOfBoundsReported += item.id
                onItemShouldRemove(item.id)
            }
        }
    }

    private fun destroyItemEntry(entry: ItemBodyEntry) {
        untrackItemBody(entry.bodyId)
        safeDestroyBody(entry.bodyId)
    }

    private fun destroyShardEntry(entry: ShardBodyEntry) {
        untrackShardBody(entry.bodyId)
        safeDestroyBody(entry.bodyId)
    }

    private fun safeDestroyBody(bodyId: b2BodyId) {
        if (Box2d.b2Body_IsValid(bodyId)) {
            Box2d.b2DestroyBody(bodyId)
        }
    }

    private fun trackItemBody(id: PhysicsId, bodyId: b2BodyId) {
        itemOwnerByBodyKey[bodyKey(bodyId)] = id
    }

    private fun untrackItemBody(bodyId: b2BodyId) {
        itemOwnerByBodyKey.remove(bodyKey(bodyId))
    }

    private fun trackShardBody(id: Long, bodyId: b2BodyId) {
        shardOwnerByBodyKey[bodyKey(bodyId)] = id
    }

    private fun untrackShardBody(bodyId: b2BodyId) {
        shardOwnerByBodyKey.remove(bodyKey(bodyId))
    }

    private fun bodyKey(bodyId: b2BodyId): Long {
        return Box2d.b2StoreBodyId(bodyId)
    }

    private fun bodyCenterPx(bodyId: b2BodyId): Offset {
        val position = Box2d.b2Body_GetPosition(bodyId)
        return Offset(
            x = position.x() * pixelsPerMeter,
            y = position.y() * pixelsPerMeter,
        )
    }

    private fun bodyAngleRad(bodyId: b2BodyId): Float {
        return Box2d.b2Rot_GetAngle(Box2d.b2Body_GetRotation(bodyId))
    }

    private fun pxToMeters(value: Float): Float {
        return value / pixelsPerMeter
    }

    private fun maybeTriggerImpactExplosion(
        id: PhysicsId,
        hitById: PhysicsId,
    ) {
        val item = items[id] ?: return
        if (!item.spec.explodeOnFirstImpact) return
        val allowedIds = item.spec.explodeOnImpactByIds
        if (allowedIds.isNotEmpty() && hitById !in allowedIds) return
        if (!firstImpactReported.add(id)) return
        onItemShouldExplode(id)
    }

    private fun resolveShardCols(
        atlasWidth: Int,
        atlasHeight: Int,
        rows: Int,
        requestedCols: Int,
        squareShards: Boolean,
    ): Int {
        if (!squareShards) {
            return requestedCols.coerceAtLeast(1)
        }
        if (atlasWidth <= 0 || atlasHeight <= 0) {
            return requestedCols.coerceAtLeast(1)
        }
        val aspect = atlasWidth.toFloat() / atlasHeight.toFloat()
        return (rows * aspect).roundToInt().coerceIn(1, 64)
    }

    private fun rotFromDegrees(degrees: Float) = rotFromRadians(degrees.toRadians())

    private fun rotFromRadians(radians: Float) = Box2d.b2MakeRot(radians)

    private fun Float.toRadians(): Float = (this * Math.PI / 180.0).toFloat()

    private fun rotate(x: Float, y: Float, angleRad: Float): Pair<Float, Float> {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Pair(
            first = x * cosA - y * sinA,
            second = x * sinA + y * cosA,
        )
    }

    private fun applyRadialImpulse(
        centerPx: Offset,
        radiusPx: Float,
        impulse: Float,
        excludedBodyKey: Long,
    ) {
        if (radiusPx <= 0f || impulse <= 0f) return

        items.values.forEach { entry ->
            applyImpulseToBodyIfInRange(
                bodyId = entry.bodyId,
                centerPx = centerPx,
                radiusPx = radiusPx,
                impulse = impulse,
                excludedBodyKey = excludedBodyKey,
            )
        }
        shards.values.forEach { entry ->
            applyImpulseToBodyIfInRange(
                bodyId = entry.bodyId,
                centerPx = centerPx,
                radiusPx = radiusPx,
                impulse = impulse,
                excludedBodyKey = excludedBodyKey,
            )
        }
    }

    private fun applyImpulseToBodyIfInRange(
        bodyId: b2BodyId,
        centerPx: Offset,
        radiusPx: Float,
        impulse: Float,
        excludedBodyKey: Long,
    ) {
        if (!Box2d.b2Body_IsValid(bodyId)) return
        if (bodyKey(bodyId) == excludedBodyKey) return
        if (Box2d.b2Body_GetType(bodyId) != b2BodyType.b2_dynamicBody) return

        val targetPx = bodyCenterPx(bodyId)
        var dx = targetPx.x - centerPx.x
        var dy = targetPx.y - centerPx.y
        var distancePx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distancePx > radiusPx) return

        if (distancePx < 0.001f) {
            val angle = random.nextFloat() * (2f * Math.PI.toFloat())
            dx = cos(angle)
            dy = sin(angle)
            distancePx = 0f
        } else {
            dx /= distancePx
            dy /= distancePx
        }

        val normalizedDistance = (distancePx / radiusPx).coerceIn(0f, 1f)
        val falloff = (1f - normalizedDistance)
        val scaledImpulse = impulse * falloff * falloff
        if (scaledImpulse <= 0.001f) return
        Box2d.b2Body_ApplyLinearImpulseToCenter(
            bodyId,
            vec2(
                x = dx * scaledImpulse,
                y = dy * scaledImpulse,
            ),
            true,
        )
    }

    private fun vec2(x: Float, y: Float): b2Vec2 {
        return b2Vec2().apply {
            x(x)
            y(y)
        }
    }

    private fun logActivatedBodiesSample() {
        items.values
            .filter { it.activated && Box2d.b2Body_IsValid(it.bodyId) }
            .forEach { entry ->
                val bodyId = entry.bodyId
                val position = Box2d.b2Body_GetPosition(bodyId)
                val velocity = Box2d.b2Body_GetLinearVelocity(bodyId)
                Log.d(
                    logTag,
                    "sample id=${entry.id} type=${Box2d.b2Body_GetType(bodyId)} " +
                        "awake=${Box2d.b2Body_IsAwake(bodyId)} " +
                        "enabled=${Box2d.b2Body_IsEnabled(bodyId)} " +
                        "gravityScale=${Box2d.b2Body_GetGravityScale(bodyId)} " +
                        "mass=${Box2d.b2Body_GetMass(bodyId)} " +
                        "pos=(${position.x()}, ${position.y()}) " +
                        "vel=(${velocity.x()}, ${velocity.y()})",
                )
            }
    }
}

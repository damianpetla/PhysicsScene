package dev.damianpetla.physicsscene

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.view.drawToBitmap
import dev.damianpetla.physicsscene.engine.FixedStepAccumulator
import dev.damianpetla.physicsscene.engine.LayoutPhysicsNode
import dev.damianpetla.physicsscene.engine.PhysicsWorldController
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsItemEvent
import dev.damianpetla.physicsscene.api.PhysicsItemEventType
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import dev.damianpetla.physicsscene.api.ShardColliderShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

private const val SNAPSHOT_LOG_TAG = "PhysicsSnapshot"
private const val PHYSICS_LOG_TAG = "PhysicsScene"

private data class RegisteredPhysicsNode(
    val id: String,
    var spec: PhysicsBodySpec,
    var sizePx: IntSize = IntSize.Zero,
    var boundsInRoot: Rect? = null,
    var baseRotationDegrees: Float = 0f,
    var captureImage: suspend () -> ImageBitmap? = { null },
)

@Composable
fun PhysicsScene(
    modifier: Modifier = Modifier,
    state: PhysicsSceneState = rememberPhysicsSceneState(),
    onItemEvent: (PhysicsItemEvent) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val nodeRegistry = remember { mutableStateMapOf<String, RegisteredPhysicsNode>() }
    val nodeTransforms = remember { mutableStateMapOf<String, PhysicsNodeVisualTransform>() }
    val explodingIds = remember { mutableSetOf<String>() }
    val activationRetryCount = remember { mutableMapOf<String, Int>() }
    var sceneGeneration by remember { mutableLongStateOf(0L) }

    var worldSizePx by remember { mutableStateOf(IntSize.Zero) }
    var worldOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val composeView = LocalView.current

    val controller = remember(state) {
        PhysicsWorldController(
            pixelsPerMeter = state.pixelsPerMeter,
            gravityPxPerSecondSq = state.gravityPxPerSecondSq,
            onItemShouldExplode = { id -> state.explode(id) },
            onItemShouldRemove = { id -> state.remove(id) },
            onShardHit = { _, hitterId ->
                onItemEvent(PhysicsItemEvent(hitterId, PhysicsItemEventType.ShardHit))
            },
            onShardDropped = { ownerId ->
                onItemEvent(PhysicsItemEvent(ownerId, PhysicsItemEventType.ShardDropped))
            },
        )
    }

    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
        }
    }

    val layoutContext = remember(state) {
        object : PhysicsSceneContext {
            override val state: PhysicsSceneState = state

            override fun onNodeRegistration(
                id: String,
                spec: PhysicsBodySpec,
                captureImage: suspend () -> ImageBitmap?,
            ) {
                val node = nodeRegistry[id]
                if (node == null) {
                    nodeRegistry[id] = RegisteredPhysicsNode(
                        id = id,
                        spec = spec,
                        captureImage = captureImage,
                    )
                } else {
                    node.spec = spec
                    node.captureImage = captureImage
                }
            }

            override fun onNodeBoundsInRoot(
                id: String,
                boundsInRoot: Rect,
                sizePx: IntSize,
                baseRotationDegrees: Float,
            ) {
                val node = nodeRegistry[id]
                if (node == null) {
                    nodeRegistry[id] = RegisteredPhysicsNode(
                        id = id,
                        spec = PhysicsBodySpec(),
                        sizePx = sizePx,
                        boundsInRoot = boundsInRoot,
                        baseRotationDegrees = baseRotationDegrees,
                    )
                } else {
                    node.sizePx = sizePx
                    node.boundsInRoot = boundsInRoot
                    node.baseRotationDegrees = baseRotationDegrees
                }
            }

            override fun onNodeDisposed(id: String) {
                nodeRegistry.remove(id)
                nodeTransforms.remove(id)
            }

            override fun visualTransformFor(id: String): PhysicsNodeVisualTransform {
                return nodeTransforms[id] ?: PhysicsNodeVisualTransform()
            }
        }
    }

    LaunchedEffect(controller, state) {
        val fixedStep = 1f / state.fixedStepHz.toFloat()
        val accumulator = FixedStepAccumulator(
            fixedStepSeconds = fixedStep,
            maxSubSteps = state.maxSubStepsPerFrame,
        )

        var previousFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) {
                    previousFrameNanos = frameNanos
                }

                val deltaSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                previousFrameNanos = frameNanos

                syncNodesToWorld(
                    controller = controller,
                    nodeRegistry = nodeRegistry,
                    worldSizePx = worldSizePx,
                    worldOriginInRoot = worldOriginInRoot,
                )

                accumulator.onFrame(deltaSeconds) { stepSeconds ->
                    controller.step(stepSeconds)
                }

                updateNodeTransforms(
                    controller = controller,
                    state = state,
                    nodeRegistry = nodeRegistry,
                    nodeTransforms = nodeTransforms,
                    worldOriginInRoot = worldOriginInRoot,
                )
                state.setShardSnapshots(
                    snapshots = controller.allShardSnapshots().map { shard ->
                        PhysicsShardSnapshot(
                            id = shard.id,
                            ownerId = shard.ownerId,
                            centerPx = shard.centerPx,
                        )
                    },
                )

                state.frameTickNanos = frameNanos
            }
        }
    }

    LaunchedEffect(controller, state) {
        state.commandFlow.collect { command ->
            when (command) {
                is PhysicsCommand.Activate -> {
                    val activated = controller.activate(command.id)
                    if (activated) {
                        activationRetryCount.remove(command.id)
                        state.setLifecycle(command.id, PhysicsLifecycleState.Falling)
                        onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Activated))
                    } else {
                        val attempts = (activationRetryCount[command.id] ?: 0) + 1
                        activationRetryCount[command.id] = attempts
                        if (attempts <= 120) {
                            if (attempts == 1 || attempts % 20 == 0) {
                                val node = nodeRegistry[command.id]
                                Log.d(
                                    PHYSICS_LOG_TAG,
                                    "activate retry id=${command.id} attempt=$attempts " +
                                        "registered=${node != null} bounds=${node?.boundsInRoot != null} " +
                                        "size=${node?.sizePx}",
                                )
                            }
                            launch {
                                delay(16)
                                state.activateBody(command.id)
                            }
                        } else {
                            Log.w(
                                PHYSICS_LOG_TAG,
                                "activate gave up id=${command.id} attempts=$attempts",
                            )
                        }
                    }
                }

                is PhysicsCommand.Explode -> {
                    if (!explodingIds.add(command.id)) {
                        return@collect
                    }

                    launch {
                        val generationAtLaunch = sceneGeneration
                        val spec = controller.bodySpecFor(command.id)?.explosionSpec
                        if (spec == null) {
                            state.setLifecycle(command.id, PhysicsLifecycleState.Removed)
                            onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Removed))
                            explodingIds.remove(command.id)
                            return@launch
                        }

                        val atlas = captureAtlasForExplosion(
                            node = nodeRegistry[command.id],
                            composeView = composeView,
                            visualTransform = nodeTransforms[command.id] ?: PhysicsNodeVisualTransform(),
                        )
                        if (generationAtLaunch != sceneGeneration) {
                            explodingIds.remove(command.id)
                            return@launch
                        }
                        if (atlas == null) {
                            delay(120)
                            controller.remove(command.id)
                            state.setLifecycle(command.id, PhysicsLifecycleState.Removed)
                            onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Removed))
                            explodingIds.remove(command.id)
                            return@launch
                        }

                        val didShatter = controller.shatter(
                            id = command.id,
                            atlas = atlas,
                            explosionSpec = spec,
                        )
                        val shardCount = controller.allShardSnapshots().count { it.ownerId == command.id }
                        Log.d(
                            SNAPSHOT_LOG_TAG,
                            "id=${command.id} shatter didShatter=$didShatter shards=$shardCount",
                        )

                        if (!didShatter) {
                            controller.remove(command.id)
                            state.setLifecycle(command.id, PhysicsLifecycleState.Removed)
                            onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Removed))
                            explodingIds.remove(command.id)
                            return@launch
                        }

                        state.setLifecycle(command.id, PhysicsLifecycleState.Shattering)
                        onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.ShatteringStarted))
                        state.setLifecycle(command.id, PhysicsLifecycleState.Removed)
                        onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Removed))
                        explodingIds.remove(command.id)
                    }
                }

                is PhysicsCommand.Remove -> {
                    controller.remove(command.id)
                    state.setLifecycle(command.id, PhysicsLifecycleState.Removed)
                    onItemEvent(PhysicsItemEvent(command.id, PhysicsItemEventType.Removed))
                }

                is PhysicsCommand.ApplyLinearImpulse -> {
                    controller.applyLinearImpulse(
                        id = command.id,
                        impulsePx = command.impulsePx,
                    )
                }

                is PhysicsCommand.PulseFromBody -> {
                    controller.applyRadialImpulseFromBody(
                        originId = command.originId,
                        radiusPx = command.radiusPx,
                        impulse = command.impulse,
                    )
                }

                is PhysicsCommand.AttractShards -> {
                    controller.attractShards(
                        ownerId = command.ownerId,
                        targetPx = command.targetPx,
                        impulsePx = command.impulsePx,
                        maxDistancePx = command.maxDistancePx,
                    )
                }

                is PhysicsCommand.RemoveShard -> {
                    controller.removeShard(command.shardId)
                }

                PhysicsCommand.ResetScene -> {
                    sceneGeneration += 1L
                    controller.resetScene()
                    state.resetAllLifecycleState()
                    nodeTransforms.clear()
                    explodingIds.clear()
                    activationRetryCount.clear()
                }
            }
        }
    }

    val frameTick = state.frameTickNanos
    val shardsForFrame = remember(frameTick) { controller.allShardSnapshots() }

    androidx.compose.runtime.CompositionLocalProvider(LocalPhysicsSceneContext provides layoutContext) {
        Box(
            modifier = modifier
                .onSizeChanged { worldSizePx = it }
                .onGloballyPositioned { coordinates ->
                    worldOriginInRoot = coordinates.positionInRoot()
                },
        ) {
            content()

            Canvas(modifier = Modifier.fillMaxSize()) {
                shardsForFrame.forEach { shard ->
                    val topLeft = IntOffset(
                        x = (shard.centerPx.x - shard.sizePx.width / 2f).roundToInt(),
                        y = (shard.centerPx.y - shard.sizePx.height / 2f).roundToInt(),
                    )
                    rotate(
                        degrees = shard.angleRad * 180f / PI.toFloat(),
                        pivot = shard.centerPx,
                    ) {
                        val drawShard: () -> Unit = {
                            drawImage(
                                image = shard.atlas,
                                srcOffset = IntOffset(shard.srcRect.left, shard.srcRect.top),
                                srcSize = IntSize(
                                    width = shard.srcRect.width,
                                    height = shard.srcRect.height,
                                ),
                                dstOffset = topLeft,
                                dstSize = shard.sizePx,
                                alpha = shard.alpha,
                                colorFilter = null,
                            )
                        }
                        if (shard.colliderShape == ShardColliderShape.Circle) {
                            val ovalPath = Path().apply {
                                addOval(
                                    Rect(
                                        left = topLeft.x.toFloat(),
                                        top = topLeft.y.toFloat(),
                                        right = topLeft.x + shard.sizePx.width.toFloat(),
                                        bottom = topLeft.y + shard.sizePx.height.toFloat(),
                                    ),
                                )
                            }
                            clipPath(path = ovalPath) {
                                drawShard()
                            }
                        } else {
                            drawShard()
                        }
                    }
                }
            }
        }
    }
}

private fun syncNodesToWorld(
    controller: PhysicsWorldController,
    nodeRegistry: Map<String, RegisteredPhysicsNode>,
    worldSizePx: IntSize,
    worldOriginInRoot: Offset,
) {
    if (worldSizePx.width <= 0 || worldSizePx.height <= 0) {
        return
    }

    val nodes = nodeRegistry.values.mapNotNull { node ->
        val bounds = node.boundsInRoot ?: return@mapNotNull null
        if (node.sizePx.width <= 0 || node.sizePx.height <= 0) {
            return@mapNotNull null
        }

        val centerLocal = Offset(
            x = bounds.center.x - worldOriginInRoot.x,
            y = bounds.center.y - worldOriginInRoot.y,
        )

        LayoutPhysicsNode(
            id = node.id,
            spec = node.spec,
            sizePx = node.sizePx,
            initialCenterPx = centerLocal,
            initialRotationDegrees = node.baseRotationDegrees,
        )
    }

    controller.syncLayout(
        worldSizePx = worldSizePx,
        nodes = nodes,
    )
}

private fun updateNodeTransforms(
    controller: PhysicsWorldController,
    state: PhysicsSceneState,
    nodeRegistry: Map<String, RegisteredPhysicsNode>,
    nodeTransforms: MutableMap<String, PhysicsNodeVisualTransform>,
    worldOriginInRoot: Offset,
) {
    val snapshots = controller.allBodySnapshots()
    state.setBodySnapshots(
        snapshots = snapshots.mapValues { (_, snapshot) ->
            PhysicsBodySnapshot(
                centerPx = snapshot.centerPx,
                angleRad = snapshot.angleRad,
            )
        },
    )
    val activeIds = HashSet<String>(nodeRegistry.size)

    nodeRegistry.values.forEach { node ->
        val bounds = node.boundsInRoot ?: return@forEach
        val baseCenterLocal = Offset(
            x = bounds.center.x - worldOriginInRoot.x,
            y = bounds.center.y - worldOriginInRoot.y,
        )
        val snapshot = snapshots[node.id]
        val transform = if (snapshot == null) {
            PhysicsNodeVisualTransform()
        } else {
            PhysicsNodeVisualTransform(
                translationX = snapshot.centerPx.x - baseCenterLocal.x,
                translationY = snapshot.centerPx.y - baseCenterLocal.y,
                rotationDegrees = normalizeDegrees(snapshot.angleRad * 180f / PI.toFloat() - node.baseRotationDegrees),
            )
        }

        nodeTransforms[node.id] = transform
        activeIds += node.id
    }

    val staleIds = nodeTransforms.keys.filterNot { it in activeIds }
    staleIds.forEach { nodeTransforms.remove(it) }
}

private fun normalizeDegrees(value: Float): Float {
    var result = value % 360f
    if (result <= -180f) result += 360f
    if (result > 180f) result -= 360f
    return result
}

private suspend fun captureAtlasForExplosion(
    node: RegisteredPhysicsNode?,
    composeView: View,
    visualTransform: PhysicsNodeVisualTransform,
): ImageBitmap? {
    if (node == null) {
        Log.w(SNAPSHOT_LOG_TAG, "captureAtlasForExplosion: missing node")
        return null
    }

    val layerCapture = node.captureImage.invoke()
    if (layerCapture != null && layerCapture.width > 1 && layerCapture.height > 1) {
        if (isLikelyNonEmpty(layerCapture)) {
            Log.d(
                SNAPSHOT_LOG_TAG,
                "id=${node.id} snapshot=GraphicsLayer size=${layerCapture.width}x${layerCapture.height}",
            )
            return layerCapture
        }
        Log.w(
            SNAPSHOT_LOG_TAG,
            "id=${node.id} snapshot=GraphicsLayer looked empty, trying drawToBitmap fallback",
        )
    }
    Log.d(SNAPSHOT_LOG_TAG, "id=${node.id} snapshot=GraphicsLayer failed, trying drawToBitmap fallback")

    val bounds = node.boundsInRoot ?: run {
        Log.w(SNAPSHOT_LOG_TAG, "id=${node.id} snapshot=fallback skipped (missing bounds)")
        return null
    }
    val transformedBounds = transformedBoundsInRoot(
        bounds = bounds,
        transform = visualTransform,
    )
    return captureFromRootView(
        id = node.id,
        composeView = composeView,
        boundsInRoot = transformedBounds,
    )
}

private fun captureFromRootView(
    id: String,
    composeView: View,
    boundsInRoot: Rect,
): ImageBitmap? {
    if (boundsInRoot.width <= 1f || boundsInRoot.height <= 1f) {
        Log.w(SNAPSHOT_LOG_TAG, "id=$id snapshot=fallback skipped (invalid bounds=$boundsInRoot)")
        return null
    }

    val source = runCatching {
        composeView.drawToBitmap(config = Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: run {
        Log.w(SNAPSHOT_LOG_TAG, "id=$id snapshot=fallback drawToBitmap failed")
        return null
    }

    val left = boundsInRoot.left.roundToInt().coerceIn(0, source.width)
    val top = boundsInRoot.top.roundToInt().coerceIn(0, source.height)
    val right = boundsInRoot.right.roundToInt().coerceIn(0, source.width)
    val bottom = boundsInRoot.bottom.roundToInt().coerceIn(0, source.height)
    if (right <= left || bottom <= top) {
        Log.w(SNAPSHOT_LOG_TAG, "id=$id snapshot=fallback crop bounds invalid after clamp")
        return null
    }

    val cropped = runCatching {
        Bitmap.createBitmap(
            source,
            left,
            top,
            right - left,
            bottom - top,
        )
    }.getOrNull() ?: run {
        Log.w(SNAPSHOT_LOG_TAG, "id=$id snapshot=fallback crop failed")
        return null
    }

    Log.d(SNAPSHOT_LOG_TAG, "id=$id snapshot=fallback(drawToBitmap) size=${cropped.width}x${cropped.height}")

    return cropped.asImageBitmap()
}

private fun isLikelyNonEmpty(image: ImageBitmap): Boolean {
    val pixelMap = image.toPixelMap()
    val stepX = (image.width / 8).coerceAtLeast(1)
    val stepY = (image.height / 8).coerceAtLeast(1)
    var opaqueSamples = 0

    var y = 0
    while (y < image.height) {
        var x = 0
        while (x < image.width) {
            if (pixelMap[x, y].alpha > 0.02f) {
                opaqueSamples += 1
                if (opaqueSamples >= 3) {
                    return true
                }
            }
            x += stepX
        }
        y += stepY
    }

    return false
}

private fun transformedBoundsInRoot(
    bounds: Rect,
    transform: PhysicsNodeVisualTransform,
): Rect {
    if (transform.translationX == 0f && transform.translationY == 0f && transform.rotationDegrees == 0f) {
        return bounds
    }

    val center = Offset(
        x = bounds.center.x + transform.translationX,
        y = bounds.center.y + transform.translationY,
    )
    val halfWidth = bounds.width / 2f
    val halfHeight = bounds.height / 2f
    val angleRad = transform.rotationDegrees * PI.toFloat() / 180f
    val cosA = kotlin.math.cos(angleRad)
    val sinA = kotlin.math.sin(angleRad)

    val corners = arrayOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
    )

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    corners.forEach { corner ->
        val rotatedX = corner.x * cosA - corner.y * sinA
        val rotatedY = corner.x * sinA + corner.y * cosA
        val x = center.x + rotatedX
        val y = center.y + rotatedY
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }

    return Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY,
    )
}

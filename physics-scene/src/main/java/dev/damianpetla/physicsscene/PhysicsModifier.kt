package dev.damianpetla.physicsscene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import dev.damianpetla.physicsscene.api.PhysicsEffect
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import kotlin.math.atan2

internal data class PhysicsNodeVisualTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationDegrees: Float = 0f,
)

internal interface PhysicsSceneContext {
    val state: PhysicsSceneState
    fun onNodeRegistration(
        id: PhysicsId,
        spec: PhysicsBodySpec,
        captureImage: suspend () -> ImageBitmap?,
    )

    fun onNodeBoundsInRoot(
        id: PhysicsId,
        boundsInRoot: Rect,
        sizePx: IntSize,
        baseRotationDegrees: Float,
    )

    fun onNodeDisposed(id: PhysicsId)

    fun visualTransformFor(id: PhysicsId): PhysicsNodeVisualTransform
}

internal val LocalPhysicsSceneContext = androidx.compose.runtime.staticCompositionLocalOf<PhysicsSceneContext?> {
    null
}

/**
 * Registers a composable as a physics body inside [PhysicsScene].
 *
 * Modifier order matters: place `physicsBody(...)` before visual draw modifiers (for example
 * `background`, `border`, `clip`) when the complete visual should be transformed by physics.
 *
 * While lifecycle is [PhysicsLifecycleState.Shattering] or [PhysicsLifecycleState.Removed], this
 * modifier hides the original composable (`alpha = 0f`) and only shard rendering is visible.
 *
 * @param id Stable body id. Reuse the same id for the same logical item across recompositions.
 * @param spec Low-level body configuration describing damping, material and shatter behavior.
 */
fun Modifier.physicsBody(
    id: PhysicsId,
    spec: PhysicsBodySpec,
): Modifier {
    return composed(
        inspectorInfo = debugInspectorInfo {
            name = "physicsBody"
            properties["id"] = id
            properties["spec"] = spec
        },
    ) {
        val context = LocalPhysicsSceneContext.current
        val lifecycle = context?.state?.lifecycleOf(id) ?: PhysicsLifecycleState.Idle
        val visualTransform = context?.visualTransformFor(id) ?: PhysicsNodeVisualTransform()
        val captureLayer = rememberGraphicsLayer()

        val captureProvider by rememberUpdatedState<suspend () -> ImageBitmap?>(
            newValue = {
                var captured: ImageBitmap? = null
                for (attempt in 0..2) {
                    val bitmap = runCatching { captureLayer.toImageBitmap() }.getOrNull()
                    if (bitmap != null && bitmap.width > 1 && bitmap.height > 1) {
                        captured = bitmap
                        break
                    }
                    if (attempt < 2) {
                        withFrameNanos { }
                    }
                }
                captured
            },
        )

        SideEffect {
            context?.onNodeRegistration(id, spec, captureProvider)
        }

        DisposableEffect(context, id) {
            onDispose {
                context?.onNodeDisposed(id)
            }
        }

        this
            .onGloballyPositioned { coordinates ->
                val currentLifecycle = context?.state?.lifecycleOf(id) ?: PhysicsLifecycleState.Idle
                if (currentLifecycle != PhysicsLifecycleState.Idle) {
                    return@onGloballyPositioned
                }
                val p0 = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                val pX = coordinates.localToRoot(
                    androidx.compose.ui.geometry.Offset(
                        x = coordinates.size.width.toFloat().coerceAtLeast(1f),
                        y = 0f,
                    ),
                )
                val baseRotationDegrees = atan2(
                    y = pX.y - p0.y,
                    x = pX.x - p0.x,
                ) * 180f / Math.PI.toFloat()
                context?.onNodeBoundsInRoot(
                    id = id,
                    boundsInRoot = coordinates.boundsInRoot(),
                    sizePx = coordinates.size,
                    baseRotationDegrees = baseRotationDegrees,
                )
            }
            .drawWithContent {
                captureLayer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = size.toIntSize(),
                ) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(captureLayer)
            }
            .graphicsLayer {
                translationX = visualTransform.translationX
                translationY = visualTransform.translationY
                rotationZ = visualTransform.rotationDegrees
                alpha = if (lifecycle == PhysicsLifecycleState.Shattering || lifecycle == PhysicsLifecycleState.Removed) {
                    0f
                } else {
                    1f
                }
            }
    }
}

/**
 * Registers a composable as a physics body by using a high-level [PhysicsEffect] preset.
 *
 * @param id Stable body id. Reuse the same id for the same logical item across recompositions.
 * @param effect Effect preset that transforms [baseSpec] into the final [PhysicsBodySpec].
 * @param baseSpec Base low-level spec used as input for [effect].
 */
fun Modifier.physicsBody(
    id: PhysicsId,
    effect: PhysicsEffect,
    baseSpec: PhysicsBodySpec = PhysicsBodySpec(),
): Modifier {
    return physicsBody(
        id = id,
        spec = effect.bodySpec(baseSpec),
    )
}

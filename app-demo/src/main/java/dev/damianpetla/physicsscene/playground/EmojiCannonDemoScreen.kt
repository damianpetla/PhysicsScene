package dev.damianpetla.physicsscene.playground

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.damianpetla.physicsscene.PhysicsScene
import dev.damianpetla.physicsscene.PhysicsSceneState
import dev.damianpetla.physicsscene.api.ExplosionSpec
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import dev.damianpetla.physicsscene.api.PhysicsCollisionIds
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsItemEvent
import dev.damianpetla.physicsscene.api.PhysicsItemEventType
import dev.damianpetla.physicsscene.api.ShardColliderShape
import dev.damianpetla.physicsscene.physicsBody
import dev.damianpetla.physicsscene.rememberPhysicsSceneState
import dev.damianpetla.physicsscene.ui.theme.PhysicsSceneTheme
import kotlin.random.Random
import kotlin.math.sqrt

private const val CANNON_SHOT_ID_PREFIX = "emoji_cannon_shot_"
private const val CANNON_SHARD_TTL_MS = 2_000
private const val CANNON_SWEEP_DURATION_MS = 5_600
private const val CANNON_MAX_ACTIVE_SHOTS = 24
private const val CANNON_PIXELS_PER_METER = 98f
private const val CANNON_GRAVITY_Y_PX = 2_050f
private const val CANNON_PROJECTILE_DENSITY = 0.75f
private const val CANNON_MIN_RISE_SCREEN_FRACTION = 0.55f
private const val CANNON_MAX_RISE_SAFETY = 0.94f
private const val CANNON_INWARD_X_SPEED = 260f
private const val CANNON_X_SPEED_JITTER = 55f
private val CANNON_TOP_CLEARANCE = 42.dp
private val CANNON_PROJECTILE_SIZE = 44.dp
private val CANNON_SPAWN_OFFSET_Y = (-188).dp

private val cannonEmojiPool = listOf(
    "\uD83D\uDE00", // 😀
    "\uD83D\uDD25", // 🔥
    "\uD83D\uDE80", // 🚀
    "\uD83D\uDCA5", // 💥
    "\uD83E\uDDE0", // 🧠
    "\uD83C\uDF55", // 🍕
    "\uD83D\uDC7E", // 👾
)

private data class EmojiShot(
    val id: PhysicsId,
    val emoji: String,
    val spawnOffsetX: Dp,
    val spawnOffsetY: Dp,
    val impulsePx: Offset,
)

@Composable
fun EmojiCannonDemoScreen(
    title: String,
    onBackClick: () -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DemoTopBar() {
        TopAppBar(
            title = { Text(text = title) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Text(text = "\u2190")
                }
            },
        )
    }

    val physicsState = rememberPhysicsSceneState(
        pixelsPerMeter = CANNON_PIXELS_PER_METER,
        gravityPxPerSecondSq = Offset(0f, CANNON_GRAVITY_Y_PX),
        fixedStepHz = 60,
        maxSubStepsPerFrame = 5,
    )
    val random = remember { Random(System.nanoTime()) }
    val shots = remember { mutableStateListOf<EmojiShot>() }
    val impulseAppliedIds = remember { mutableSetOf<PhysicsId>() }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val projectileSizePx = with(density) { CANNON_PROJECTILE_SIZE.toPx() }
    val topClearancePx = with(density) { CANNON_TOP_CLEARANCE.toPx() }
    val spawnOffsetYPx = with(density) { CANNON_SPAWN_OFFSET_Y.toPx() }
    var worldHeightPx by remember { mutableFloatStateOf(0f) }

    var shotCounter by remember { mutableIntStateOf(0) }
    var firedCount by remember { mutableIntStateOf(0) }
    var explodedCount by remember { mutableIntStateOf(0) }

    val sweepTransition = rememberInfiniteTransition(label = "emoji-cannon-sweep")
    val sweep by sweepTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = CANNON_SWEEP_DURATION_MS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emoji-cannon-sweep-value",
    )
    val cannonOffsetX = 132.dp * sweep

    fun fireEmoji() {
        if (shots.size >= CANNON_MAX_ACTIVE_SHOTS) {
            val oldest = shots.removeAt(0)
            impulseAppliedIds -= oldest.id
            physicsState.remove(oldest.id)
        }

        val newId = "$CANNON_SHOT_ID_PREFIX$shotCounter"
        shotCounter += 1
        firedCount += 1

        val emoji = cannonEmojiPool.random(random)
        val sizeMeters = projectileSizePx / CANNON_PIXELS_PER_METER
        val areaMeters2 = sizeMeters * sizeMeters
        val estimatedMass = (CANNON_PROJECTILE_DENSITY * areaMeters2).coerceAtLeast(0.05f)

        val viewportHeightPx = if (worldHeightPx > 1f) worldHeightPx else screenHeightPx
        val projectileRadiusPx = projectileSizePx / 2f
        val spawnCenterYPx = viewportHeightPx - projectileRadiusPx + spawnOffsetYPx
        val topSafeYPx = projectileRadiusPx + topClearancePx
        val maxSafeRisePx = (spawnCenterYPx - topSafeYPx).coerceAtLeast(1f)
        val maxRisePx = (maxSafeRisePx * CANNON_MAX_RISE_SAFETY).coerceAtLeast(1f)
        val minRisePx = (viewportHeightPx * CANNON_MIN_RISE_SCREEN_FRACTION).coerceAtMost(maxRisePx)
        val targetRisePx = if (maxRisePx > minRisePx) {
            minRisePx + (maxRisePx - minRisePx) * random.nextFloat()
        } else {
            maxRisePx
        }
        val upwardVelocityPx = sqrt(2f * CANNON_GRAVITY_Y_PX * targetRisePx)
        val impulseY = -estimatedMass * upwardVelocityPx

        val inwardVelocityPx = -sweep * CANNON_INWARD_X_SPEED
        val jitterVelocityPx = (random.nextFloat() * 2f - 1f) * CANNON_X_SPEED_JITTER
        val impulseX = estimatedMass * (inwardVelocityPx + jitterVelocityPx)

        shots += EmojiShot(
            id = newId,
            emoji = emoji,
            spawnOffsetX = cannonOffsetX,
            spawnOffsetY = CANNON_SPAWN_OFFSET_Y,
            impulsePx = Offset(x = impulseX, y = impulseY),
        )
    }

    fun resetDemo() {
        physicsState.resetScene()
        shots.clear()
        impulseAppliedIds.clear()
        shotCounter = 0
        firedCount = 0
        explodedCount = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DemoTopBar() },
    ) { paddingValues ->
        PhysicsScene(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues)
                .background(Color(0xFF0D1322)),
            state = physicsState,
            onItemEvent = { event: PhysicsItemEvent ->
                if (!event.id.startsWith(CANNON_SHOT_ID_PREFIX)) return@PhysicsScene
                if (event.type == PhysicsItemEventType.Removed) {
                    impulseAppliedIds -= event.id
                    if (shots.removeAll { it.id == event.id }) {
                        explodedCount += 1
                    }
                }
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        worldHeightPx = size.height.toFloat()
                    },
            ) {
                shots.forEach { shot ->
                    key(shot.id) {
                        EmojiShotNode(
                            shot = shot,
                            physicsState = physicsState,
                            shouldApplyImpulse = shot.id !in impulseAppliedIds,
                            onImpulseApplied = { impulseAppliedIds += shot.id },
                        )
                    }
                }

                CannonVisual(
                    offsetX = cannonOffsetX,
                    sweep = sweep,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-132).dp),
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .physicsBody(
                            id = "emoji_cannon_hud",
                            spec = PhysicsBodySpec(
                                bodyType = PhysicsBodyType.Static,
                                isSensor = true,
                            ),
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xCC121A2A),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { fireEmoji() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = "Strzel Emoji")
                            }
                            OutlinedButton(
                                onClick = { resetDemo() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = "Reset")
                            }
                        }

                        Text(
                            text = "Wystrzelone: $firedCount   Rozbite: $explodedCount   Aktywne: ${shots.size}",
                            color = Color(0xFFE3ECFF),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CannonVisual(
    offsetX: Dp,
    sweep: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset(x = offsetX)
            .size(width = 116.dp, height = 78.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .rotate(sweep * 16f)
                .size(width = 64.dp, height = 18.dp)
                .background(
                    color = Color(0xFF2C374F),
                    shape = RoundedCornerShape(10.dp),
                ),
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 116.dp, height = 56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F2A44),
                contentColor = Color(0xFFEFF4FF),
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\uD83D\uDCA3",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.EmojiShotNode(
    shot: EmojiShot,
    physicsState: PhysicsSceneState,
    shouldApplyImpulse: Boolean,
    onImpulseApplied: () -> Unit,
) {
    val projectileSpec = remember { emojiProjectileSpec() }

    LaunchedEffect(shot.id, shouldApplyImpulse) {
        if (!shouldApplyImpulse) return@LaunchedEffect
        repeat(24) {
            withFrameNanos { }
            if (physicsState.bodySnapshot(shot.id) == null) return@repeat
            onImpulseApplied()
            physicsState.applyLinearImpulse(
                id = shot.id,
                impulsePx = shot.impulsePx,
            )
            return@LaunchedEffect
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(
                x = shot.spawnOffsetX,
                y = shot.spawnOffsetY,
            )
            .size(CANNON_PROJECTILE_SIZE)
            .physicsBody(
                id = shot.id,
                spec = projectileSpec,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shot.emoji,
            fontSize = 30.sp,
        )
    }
}

private fun emojiProjectileSpec(): PhysicsBodySpec {
    return PhysicsBodySpec(
        bodyType = PhysicsBodyType.Dynamic,
        density = CANNON_PROJECTILE_DENSITY,
        friction = 0.08f,
        restitution = 0.66f,
        linearDamping = 0.04f,
        angularDamping = 0.05f,
        explodeOnFirstImpact = true,
        explodeOnImpactByIds = setOf(PhysicsCollisionIds.WORLD_BOUNDS),
        explosionSpec = ExplosionSpec(
            shardsRows = 4,
            shardsCols = 4,
            squareShards = true,
            shardColliderShape = ShardColliderShape.Box,
            shardTtlMs = CANNON_SHARD_TTL_MS,
            impulseMin = 0.08f,
            impulseMax = 0.18f,
        ),
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun EmojiCannonDemoScreenPreview() {
    PhysicsSceneTheme {
        EmojiCannonDemoScreen(
            title = "Emoji Cannon",
            onBackClick = {},
        )
    }
}

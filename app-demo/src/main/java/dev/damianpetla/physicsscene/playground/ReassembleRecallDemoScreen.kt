package dev.damianpetla.physicsscene.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.damianpetla.physicsscene.PhysicsScene
import dev.damianpetla.physicsscene.api.BodyRemoved
import dev.damianpetla.physicsscene.api.CenterBurstEffect
import dev.damianpetla.physicsscene.api.CustomEffect
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsSceneEvent
import dev.damianpetla.physicsscene.api.ShardColliderShape
import dev.damianpetla.physicsscene.physicsBody
import dev.damianpetla.physicsscene.rememberPhysicsSceneState
import dev.damianpetla.physicsscene.ui.theme.PhysicsSceneTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val RECALL_BUTTON_ID: PhysicsId = "reassemble_button"
private const val RECALL_ANCHOR_ID: PhysicsId = "reassemble_anchor"

private const val RECALL_DELAY_MS = 3_000L
private const val RECALL_PULL_IMPULSE_PX = 28f
private const val RECALL_PULL_RADIUS_PX = 12_000f
private const val RECALL_CAPTURE_MARGIN_PX = 14f
private const val RECALL_SHARDS_ROWS = 4
private const val RECALL_SHARDS_COLS = 8

private val RECALL_ANCHOR_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    isSensor = true,
)
private val RECALL_HUD_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    isSensor = true,
)
private val REASSEMBLE_EFFECT: CustomEffect = run {
    val base = CenterBurstEffect(
        shardsRows = RECALL_SHARDS_ROWS,
        shardsCols = RECALL_SHARDS_COLS,
        squareShards = true,
        impulseMin = 0.12f,
        impulseMax = 0.28f,
    )
    CustomEffect { spec ->
        val bodySpec = base.bodySpec(spec)
        bodySpec.copy(
            explodeOnFirstImpact = false,
            friction = 0.52f,
            restitution = 0.08f,
            linearDamping = 0.22f,
            angularDamping = 0.24f,
            explosionSpec = bodySpec.explosionSpec.copy(
                shardTtlMs = -1,
                impulseMin = 0.12f,
                impulseMax = 0.28f,
                shardColliderShape = ShardColliderShape.Box,
            ),
        )
    }
}

private enum class RecallPhase {
    Idle,
    WaitingRecall,
    Recalling,
}

@Composable
fun ReassembleRecallDemoScreen(
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
        pixelsPerMeter = 100f,
        gravityPxPerSecondSq = Offset.Zero,
        fixedStepHz = 60,
        maxSubStepsPerFrame = 5,
    )

    val buttonWidthDp = 188.dp
    val buttonHeightDp = 58.dp
    val density = LocalDensity.current
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val buttonHeightPx = with(density) { buttonHeightDp.toPx() }

    var phase by remember { mutableStateOf(RecallPhase.Idle) }
    var phaseStartedAtMs by remember { mutableLongStateOf(0L) }
    var phaseLabel by remember { mutableStateOf("Ready") }
    var shardTotal by remember { mutableIntStateOf(0) }
    var shardsReturned by remember { mutableIntStateOf(0) }
    var rebuildProgress by remember { mutableFloatStateOf(0f) }
    val consumedShardIds = remember { mutableSetOf<Long>() }

    fun resetDemo() {
        physicsState.resetScene()
        phase = RecallPhase.Idle
        phaseStartedAtMs = 0L
        phaseLabel = "Ready"
        shardTotal = 0
        shardsReturned = 0
        rebuildProgress = 0f
        consumedShardIds.clear()
    }

    LaunchedEffect(phase, phaseStartedAtMs) {
        if (phase != RecallPhase.WaitingRecall) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - phaseStartedAtMs
        val remaining = (RECALL_DELAY_MS - elapsed).coerceAtLeast(0L)
        delay(remaining)
        if (phase == RecallPhase.WaitingRecall) {
            phase = RecallPhase.Recalling
            phaseLabel = "Recalling shards"
        }
    }

    LaunchedEffect(phase, buttonWidthPx, buttonHeightPx) {
        while (true) {
            withFrameNanos { }
            if (phase != RecallPhase.Recalling) continue

            val anchorCenter = physicsState.bodySnapshot(RECALL_ANCHOR_ID)?.centerPx ?: continue
            val shards = physicsState.shardSnapshots(ownerId = RECALL_BUTTON_ID)
            if (shardTotal == 0 && shards.isNotEmpty()) {
                shardTotal = shards.size
            }
            if (shards.isEmpty()) {
                if (shardTotal > 0 && shardsReturned >= shardTotal) {
                    rebuildProgress = 1f
                    physicsState.resetScene()
                    phase = RecallPhase.Idle
                    phaseLabel = "Reassembled"
                    shardTotal = 0
                    shardsReturned = 0
                    consumedShardIds.clear()
                }
                continue
            }

            physicsState.attractShards(
                ownerId = RECALL_BUTTON_ID,
                targetPx = anchorCenter,
                impulsePx = RECALL_PULL_IMPULSE_PX,
                maxDistancePx = RECALL_PULL_RADIUS_PX,
            )

            val halfWidth = buttonWidthPx / 2f + RECALL_CAPTURE_MARGIN_PX
            val halfHeight = buttonHeightPx / 2f + RECALL_CAPTURE_MARGIN_PX

            val enteredBoxShards = shards.filter { shard ->
                shard.id !in consumedShardIds &&
                    abs(shard.centerPx.x - anchorCenter.x) <= halfWidth &&
                    abs(shard.centerPx.y - anchorCenter.y) <= halfHeight
            }
            enteredBoxShards.forEach { shard ->
                consumedShardIds += shard.id
                physicsState.removeShard(shard.id)
                shardsReturned += 1
            }

            if (shardTotal > 0) {
                rebuildProgress = (shardsReturned.toFloat() / shardTotal.toFloat()).coerceIn(0f, 1f)
                phaseLabel = "Reassembling ${(rebuildProgress * 100f).toInt()}%"
            }

            if (shardTotal > 0 && shardsReturned >= shardTotal) {
                rebuildProgress = 1f
                physicsState.resetScene()
                phase = RecallPhase.Idle
                phaseLabel = "Reassembled"
                shardTotal = 0
                shardsReturned = 0
                consumedShardIds.clear()
            }
        }
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
                .background(MaterialTheme.colorScheme.surface),
            state = physicsState,
            onEvent = { event: PhysicsSceneEvent ->
                if (event is BodyRemoved && event.id == RECALL_BUTTON_ID) {
                    phaseLabel = "Shattered. Recall starts in 3s"
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = buttonWidthDp, height = buttonHeightDp)
                        .physicsBody(
                            id = RECALL_ANCHOR_ID,
                            spec = RECALL_ANCHOR_SPEC,
                        ),
                )

                Button(
                    enabled = phase == RecallPhase.Idle,
                    onClick = {
                        if (phase != RecallPhase.Idle) return@Button
                        physicsState.explode(RECALL_BUTTON_ID)
                        phase = RecallPhase.WaitingRecall
                        phaseStartedAtMs = System.currentTimeMillis()
                        phaseLabel = "Shattered. Recall starts in 3s"
                        shardTotal = 0
                        shardsReturned = 0
                        rebuildProgress = 0f
                        consumedShardIds.clear()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = buttonWidthDp, height = buttonHeightDp)
                        .physicsBody(
                            id = RECALL_BUTTON_ID,
                            effect = REASSEMBLE_EFFECT,
                        ),
                ) {
                    Text(text = "Recall Shatter")
                }

                if (phase != RecallPhase.Idle && rebuildProgress > 0f) {
                    val clamped = rebuildProgress.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = buttonWidthDp, height = buttonHeightDp)
                            .graphicsLayer {
                                alpha = clamped
                                val scale = 0.84f + clamped * 0.16f
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(
                                color = Color(0xFF2E7D32),
                                shape = MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Recall Shatter",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .physicsBody(
                            id = "reassemble_hud",
                            spec = RECALL_HUD_SPEC,
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Phase: $phaseLabel",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Shards returned: $shardsReturned / $shardTotal",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FilledTonalButton(
                            onClick = { resetDemo() },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(text = "Reset")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ReassembleRecallDemoScreenPreview() {
    PhysicsSceneTheme {
        ReassembleRecallDemoScreen(
            title = "Shard Recall",
            onBackClick = {},
        )
    }
}

package dev.damianpetla.physicsscene.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.damianpetla.physicsscene.PhysicsScene
import dev.damianpetla.physicsscene.api.BodyActivated
import dev.damianpetla.physicsscene.api.BodyRemoved
import dev.damianpetla.physicsscene.api.BodyShatteringStarted
import dev.damianpetla.physicsscene.api.FallingShatterEffect
import dev.damianpetla.physicsscene.api.PhysicsBodySpec
import dev.damianpetla.physicsscene.api.PhysicsBodyType
import dev.damianpetla.physicsscene.api.PhysicsEffect
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import dev.damianpetla.physicsscene.api.PhysicsSceneEvent
import dev.damianpetla.physicsscene.api.ShardColliderShape
import dev.damianpetla.physicsscene.physicsBody
import dev.damianpetla.physicsscene.rememberPhysicsSceneState
import dev.damianpetla.physicsscene.ui.theme.PhysicsSceneTheme

private const val DROP_BUTTON_A_ID: PhysicsId = "drop_button_a"
private const val DROP_BUTTON_B_ID: PhysicsId = "drop_button_b"

private val DROP_BUTTON_EFFECT_A = FallingShatterEffect(shardColliderShape = ShardColliderShape.Box)
private val DROP_BUTTON_EFFECT_B = FallingShatterEffect(shardColliderShape = ShardColliderShape.Circle)
private val OBSTACLE_TOP_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    friction = 0.16f,
    restitution = 0.24f,
)
private val OBSTACLE_MID_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    friction = 0.16f,
    restitution = 0.2f,
)
private val TEXT_BLOCK_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    friction = 0.9f,
    restitution = 0.08f,
    isSensor = true,
)
private val RESET_BUTTON_SPEC = PhysicsBodySpec(
    bodyType = PhysicsBodyType.Static,
    isSensor = true,
)
private val DEFAULT_BASE_SPEC = PhysicsBodySpec()

@Composable
fun PhysicsPlaygroundScreen(
    title: String,
    onBackClick: (() -> Unit)?,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PlaygroundTopBar() {
        TopAppBar(
            title = { Text(text = title) },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Text(text = "\u2190")
                    }
                }
            },
        )
    }

    val physicsState = rememberPhysicsSceneState(
        pixelsPerMeter = 100f,
        gravityPxPerSecondSq = Offset(0f, 2_250f),
        fixedStepHz = 60,
        maxSubStepsPerFrame = 5,
    )
    var buttonAStatus by remember { mutableStateOf(PhysicsLifecycleState.Idle) }
    var buttonBStatus by remember { mutableStateOf(PhysicsLifecycleState.Idle) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { PlaygroundTopBar() },
    ) { paddingValues ->
        PhysicsScene(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface),
            state = physicsState,
            onEvent = { event: PhysicsSceneEvent ->
                when (event) {
                    is BodyActivated -> {
                        if (event.id == DROP_BUTTON_A_ID) buttonAStatus = PhysicsLifecycleState.Falling
                        if (event.id == DROP_BUTTON_B_ID) buttonBStatus = PhysicsLifecycleState.Falling
                    }

                    is BodyShatteringStarted -> {
                        if (event.id == DROP_BUTTON_A_ID) buttonAStatus = PhysicsLifecycleState.Shattering
                        if (event.id == DROP_BUTTON_B_ID) buttonBStatus = PhysicsLifecycleState.Shattering
                    }

                    is BodyRemoved -> {
                        if (event.id == DROP_BUTTON_A_ID) buttonAStatus = PhysicsLifecycleState.Removed
                        if (event.id == DROP_BUTTON_B_ID) buttonBStatus = PhysicsLifecycleState.Removed
                    }

                    else -> Unit
                }
            },
        ) {
            PhysicsPlaygroundContent(
                buttonAStatus = buttonAStatus,
                buttonBStatus = buttonBStatus,
                onDropClick = { id -> physicsState.activateBody(id) },
                onRespawnClick = { id ->
                    physicsState.respawnBody(id)
                    if (id == DROP_BUTTON_A_ID) buttonAStatus = PhysicsLifecycleState.Idle
                    if (id == DROP_BUTTON_B_ID) buttonBStatus = PhysicsLifecycleState.Idle
                },
                onResetClick = {
                    physicsState.resetScene()
                    buttonAStatus = PhysicsLifecycleState.Idle
                    buttonBStatus = PhysicsLifecycleState.Idle
                },
            )
        }
    }
}

@Composable
private fun PhysicsPlaygroundContent(
    buttonAStatus: PhysicsLifecycleState,
    buttonBStatus: PhysicsLifecycleState,
    onDropClick: (PhysicsId) -> Unit,
    onRespawnClick: (PhysicsId) -> Unit,
    onResetClick: () -> Unit,
    enablePhysics: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onDropClick(DROP_BUTTON_A_ID) },
                        modifier = Modifier
                            .size(width = 164.dp, height = 48.dp)
                            .playgroundPhysicsBody(
                                enabled = enablePhysics,
                                id = DROP_BUTTON_A_ID,
                                effect = DROP_BUTTON_EFFECT_A,
                            ),
                    ) {
                        Text(text = "Drop A")
                    }

                    Button(
                        onClick = { onDropClick(DROP_BUTTON_B_ID) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                        modifier = Modifier
                            .size(width = 164.dp, height = 48.dp)
                            .playgroundPhysicsBody(
                                enabled = enablePhysics,
                                id = DROP_BUTTON_B_ID,
                                effect = DROP_BUTTON_EFFECT_B,
                            ),
                    ) {
                        Text(text = "Drop B")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onRespawnClick(DROP_BUTTON_A_ID) },
                        modifier = Modifier.size(width = 164.dp, height = 44.dp),
                    ) {
                        Text(text = "Respawn A")
                    }

                    OutlinedButton(
                        onClick = { onRespawnClick(DROP_BUTTON_B_ID) },
                        modifier = Modifier.size(width = 164.dp, height = 44.dp),
                    ) {
                        Text(text = "Respawn B")
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp, start = 24.dp)
                    .rotate(-25f)
                    .size(width = 220.dp, height = 34.dp)
                    .playgroundPhysicsBody(
                        enabled = enablePhysics,
                        id = "obstacle_top",
                        spec = OBSTACLE_TOP_SPEC,
                    ),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Static Platform A", style = MaterialTheme.typography.labelMedium)
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .rotate(20f)
                    .size(width = 240.dp, height = 34.dp)
                    .playgroundPhysicsBody(
                        enabled = enablePhysics,
                        id = "obstacle_mid",
                        spec = OBSTACLE_MID_SPEC,
                    ),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Static Platform B", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .playgroundPhysicsBody(
                    enabled = enablePhysics,
                    id = "text_block",
                    spec = TEXT_BLOCK_SPEC,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onResetClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .playgroundPhysicsBody(
                            enabled = enablePhysics,
                            id = "reset_scene_button",
                            spec = RESET_BUTTON_SPEC,
                        ),
                ) {
                    Text(
                        text = "\u21BB",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Physics Playground",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "A: $buttonAStatus  |  B: $buttonBStatus",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun Modifier.playgroundPhysicsBody(
    enabled: Boolean,
    id: PhysicsId,
    spec: PhysicsBodySpec,
): Modifier {
    return if (enabled) {
        this.physicsBody(id = id, spec = spec)
    } else {
        this
    }
}

private fun Modifier.playgroundPhysicsBody(
    enabled: Boolean,
    id: PhysicsId,
    effect: PhysicsEffect,
    baseSpec: PhysicsBodySpec = DEFAULT_BASE_SPEC,
): Modifier {
    return if (enabled) {
        this.physicsBody(id = id, effect = effect, baseSpec = baseSpec)
    } else {
        this
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PhysicsPlaygroundContentPreview() {
    PhysicsSceneTheme {
        PhysicsPlaygroundContent(
            buttonAStatus = PhysicsLifecycleState.Idle,
            buttonBStatus = PhysicsLifecycleState.Idle,
            onDropClick = {},
            onRespawnClick = {},
            onResetClick = {},
            enablePhysics = false,
        )
    }
}

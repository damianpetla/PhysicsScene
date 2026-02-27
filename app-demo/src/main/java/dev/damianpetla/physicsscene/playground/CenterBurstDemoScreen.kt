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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.damianpetla.physicsscene.PhysicsScene
import dev.damianpetla.physicsscene.api.CenterBurstEffect
import dev.damianpetla.physicsscene.api.PhysicsId
import dev.damianpetla.physicsscene.api.PhysicsItemEvent
import dev.damianpetla.physicsscene.api.PhysicsItemEventType
import dev.damianpetla.physicsscene.api.PhysicsLifecycleState
import dev.damianpetla.physicsscene.physicsBody
import dev.damianpetla.physicsscene.rememberPhysicsSceneState
import dev.damianpetla.physicsscene.ui.theme.PhysicsSceneTheme

private const val CENTER_BURST_BUTTON_ID: PhysicsId = "center_burst_button"

@Composable
fun CenterBurstDemoScreen(
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
    var centerStatus by remember { mutableStateOf(PhysicsLifecycleState.Idle) }

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
            onItemEvent = { event: PhysicsItemEvent ->
                if (event.id == CENTER_BURST_BUTTON_ID) {
                    centerStatus = event.type.toLifecycle()
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Button(
                    onClick = { physicsState.explode(CENTER_BURST_BUTTON_ID) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 176.dp, height = 56.dp)
                        .physicsBody(
                            id = CENTER_BURST_BUTTON_ID,
                            effect = CenterBurstEffect(),
                        ),
                ) {
                    Text(text = "Explode Center")
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Zero gravity burst with round shards",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "State: $centerStatus",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Button(
                        onClick = {
                            physicsState.resetScene()
                            centerStatus = PhysicsLifecycleState.Idle
                        },
                    ) {
                        Text(text = "Reset Scene")
                    }
                }
            }
        }
    }
}

private fun PhysicsItemEventType.toLifecycle(): PhysicsLifecycleState {
    return when (this) {
        PhysicsItemEventType.Activated -> PhysicsLifecycleState.Falling
        PhysicsItemEventType.ShatteringStarted -> PhysicsLifecycleState.Shattering
        PhysicsItemEventType.ShardHit -> PhysicsLifecycleState.Shattering
        PhysicsItemEventType.ShardDropped -> PhysicsLifecycleState.Shattering
        PhysicsItemEventType.Removed -> PhysicsLifecycleState.Removed
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CenterBurstDemoScreenPreview() {
    PhysicsSceneTheme {
        CenterBurstDemoScreen(
            title = "Center Burst",
            onBackClick = {},
        )
    }
}

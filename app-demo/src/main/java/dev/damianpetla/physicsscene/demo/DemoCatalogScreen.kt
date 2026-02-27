package dev.damianpetla.physicsscene.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.damianpetla.physicsscene.playground.CenterBurstDemoScreen
import dev.damianpetla.physicsscene.playground.EmojiCannonDemoScreen
import dev.damianpetla.physicsscene.playground.PhysicsPlaygroundScreen
import dev.damianpetla.physicsscene.playground.ReassembleRecallDemoScreen

private data class DemoEntry(
    val id: String,
    val title: String,
    val description: String,
)

private val demoEntries = listOf(
    DemoEntry(
        id = "falling_shatter",
        title = "Falling Shatter",
        description = "Preset effect: falling components that shatter into shards on first impact.",
    ),
    DemoEntry(
        id = "center_burst",
        title = "Center Burst (Zero Gravity)",
        description = "Tap center button to explode into round shards in zero-gravity world.",
    ),
    DemoEntry(
        id = "shard_recall",
        title = "Shard Recall",
        description = "Tap once to shatter, then shards are recalled back into the original shape.",
    ),
    DemoEntry(
        id = "emoji_cannon",
        title = "Emoji Cannon",
        description = "Moving bottom cannon launches random emoji that shatter on wall impact.",
    ),
)

@Composable
fun DemoCatalogScreen() {
    var selectedDemoId by remember { mutableStateOf<String?>(null) }

    if (selectedDemoId == null) {
        DemoList(
            entries = demoEntries,
            onSelect = { entry -> selectedDemoId = entry.id },
        )
        return
    }

    val selectedEntry = demoEntries.first { it.id == selectedDemoId }
    when (selectedEntry.id) {
        "falling_shatter" -> PhysicsPlaygroundScreen(
            title = selectedEntry.title,
            onBackClick = { selectedDemoId = null },
        )

        "center_burst" -> CenterBurstDemoScreen(
            title = selectedEntry.title,
            onBackClick = { selectedDemoId = null },
        )

        "shard_recall" -> ReassembleRecallDemoScreen(
            title = selectedEntry.title,
            onBackClick = { selectedDemoId = null },
        )

        "emoji_cannon" -> EmojiCannonDemoScreen(
            title = selectedEntry.title,
            onBackClick = { selectedDemoId = null },
        )
    }
}

@Composable
private fun DemoList(
    entries: List<DemoEntry>,
    onSelect: (DemoEntry) -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CatalogTopBar() {
        TopAppBar(
            title = {
                Text(text = "Physics Effects Demo")
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CatalogTopBar() },
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

package dev.damianpetla.physicsscene

import dev.damianpetla.physicsscene.engine.sliceGrid
import org.junit.Assert.assertEquals
import org.junit.Test

class ShardAtlasSlicingTest {

    @Test
    fun slicesAtlasIntoExpectedGrid() {
        val slices = sliceGrid(
            atlasWidth = 100,
            atlasHeight = 50,
            rows = 5,
            cols = 4,
        )

        assertEquals(20, slices.size)
        assertEquals(0, slices.first().srcRect.left)
        assertEquals(0, slices.first().srcRect.top)
        assertEquals(25, slices.first().srcRect.right)
        assertEquals(10, slices.first().srcRect.bottom)
        assertEquals(100, slices.last().srcRect.right)
        assertEquals(50, slices.last().srcRect.bottom)
    }
}

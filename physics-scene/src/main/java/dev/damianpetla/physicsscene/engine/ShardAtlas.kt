package dev.damianpetla.physicsscene.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal data class ShardSlice(
    val srcRect: IntRect,
    val centerOffsetPx: Offset,
    val sizePx: IntSize,
)

internal fun sliceGrid(
    atlasWidth: Int,
    atlasHeight: Int,
    rows: Int,
    cols: Int,
): List<ShardSlice> {
    require(rows > 0)
    require(cols > 0)

    val slices = ArrayList<ShardSlice>(rows * cols)
    for (row in 0 until rows) {
        val top = row * atlasHeight / rows
        val bottom = (row + 1) * atlasHeight / rows

        for (col in 0 until cols) {
            val left = col * atlasWidth / cols
            val right = (col + 1) * atlasWidth / cols
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)
            val center = Offset(
                x = left + width / 2f,
                y = top + height / 2f,
            )

            slices += ShardSlice(
                srcRect = IntRect(left = left, top = top, right = right, bottom = bottom),
                centerOffsetPx = center,
                sizePx = IntSize(width = width, height = height),
            )
        }
    }
    return slices
}

package com.sbro.emucorec.ui.settings

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

fun centeredTabScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int
): Float {
    val itemCenter = itemOffset + (itemSize / 2f)
    val viewportCenter = viewportStart + ((viewportEnd - viewportStart) / 2f)
    return itemCenter - viewportCenter
}

suspend fun LazyListState.animateScrollToCenterItem(index: Int) {
    if (index < 0) return
    var selectedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (selectedItem == null) {
        scrollToItem(index)
        withFrameNanos { }
        selectedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }

    selectedItem?.let { item ->
        val info = layoutInfo
        val delta = centeredTabScrollDelta(
            itemOffset = item.offset,
            itemSize = item.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset
        )
        if (kotlin.math.abs(delta) > 1f) {
            animateScrollBy(delta)
        }
    }
}


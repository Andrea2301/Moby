package com.example.moby.logic.readers.epub

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EpubJavascriptBridge(
    private val scope: CoroutineScope,
    private val onVirtualPageCountReady: (Int) -> Unit,
    private val onVirtualPageIndexChanged: (Int) -> Unit,
    private val onChapterBoundary: (Boolean) -> Unit,
    private val onTextSelectedRaw: (String, String, Float, Float, Float, Float) -> Unit,
    private val onSelectionClearedRaw: () -> Unit,
    private val onCenterTap: () -> Unit
) {
    @JavascriptInterface
    fun onPageCountReady(count: Int) {
        scope.launch(Dispatchers.Main) { onVirtualPageCountReady(count) }
    }

    @JavascriptInterface
    fun onVirtualPageIndexChanged(idx: Int) {
        scope.launch(Dispatchers.Main) { onVirtualPageIndexChanged(idx) }
    }

    @JavascriptInterface
    fun onChapterBoundary(reachedEndStr: String) {
        val reachedEnd = reachedEndStr == "true"
        scope.launch(Dispatchers.Main) { onChapterBoundary(reachedEnd) }
    }

    @JavascriptInterface
    fun onTextSelected(text: String, cfi: String, top: Float, left: Float, w: Float, h: Float) {
        scope.launch(Dispatchers.Main) { onTextSelectedRaw(text, cfi, left, top, w, h) }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        scope.launch(Dispatchers.Main) { onSelectionClearedRaw() }
    }

    @JavascriptInterface
    fun onTapCenter() {
        scope.launch(Dispatchers.Main) { onCenterTap() }
    }
}

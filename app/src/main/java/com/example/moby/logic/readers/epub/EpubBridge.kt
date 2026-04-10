package com.example.moby.logic.readers.epub

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EpubJavascriptBridge(
    private val scope: CoroutineScope,
    private val onVirtualPageCountReady: (Int) -> Unit,
    private val onTextSelectedRaw: (String, String, Float, Float) -> Unit,
    private val onSelectionClearedRaw: () -> Unit,
    private val onLeftTap: () -> Unit,
    private val onRightTap: () -> Unit,
    private val onCenterTap: () -> Unit
) {
    @JavascriptInterface
    fun onPageCountReady(count: Int) {
        scope.launch(Dispatchers.Main) { onVirtualPageCountReady(count) }
    }

    @JavascriptInterface
    fun onTextSelected(text: String, cfi: String, top: Float, left: Float, w: Float, h: Float) {
        scope.launch(Dispatchers.Main) { onTextSelectedRaw(text, cfi, left, top) }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        scope.launch(Dispatchers.Main) { onSelectionClearedRaw() }
    }

    @JavascriptInterface
    fun onTapLeft() {
        scope.launch(Dispatchers.Main) { onLeftTap() }
    }

    @JavascriptInterface
    fun onTapRight() {
        scope.launch(Dispatchers.Main) { onRightTap() }
    }

    @JavascriptInterface
    fun onTapCenter() {
        scope.launch(Dispatchers.Main) { onCenterTap() }
    }
}

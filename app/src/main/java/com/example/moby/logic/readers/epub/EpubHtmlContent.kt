package com.example.moby.logic.readers.epub

import com.example.moby.ui.screens.ReaderTheme
import java.util.Locale

object EpubHtmlContent {
    fun build(
        bodyContent: String,
        theme: ReaderTheme,
        fontSize: Float,
        fontFamily: String,
        lineSpacing: Float,
        isVerticalMode: Boolean,
        virtualPageIndex: Int
    ): String {
        val bgHex    = theme.toBgHex()
        val textHex  = theme.toTextHex()
        val linkHex  = theme.toLinkHex()
        val fontStack = if (fontFamily == "Sans") "sans-serif" else "Georgia, serif"
        val lineSpacingStr = String.format(Locale.US, "%.2f", lineSpacing)

        val columnStyle = if (isVerticalMode) {
            "overflow-y: auto; display: block; width: 100%;"
        } else {
            "-webkit-column-width: 100vw; column-width: 100vw; -webkit-column-gap: 0; column-gap: 0; column-fill: auto; height: 100%; overflow: visible;"
        }

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
<style>
::-webkit-scrollbar { display: none !important; }
*, *::before, *::after { box-sizing: border-box !important; }

html { 
    margin: 0 !important; padding: 0 !important; 
    width: 100vw !important; height: 100vh !important; 
    background-color: $bgHex !important;
    overflow: hidden !important;
}

body { 
    margin: 0 !important; padding: 0 !important;
    width: 100vw !important; height: 100vh !important;
    background-color: $bgHex !important; color: $textHex !important;
    font-family: $fontStack !important;
    line-height: $lineSpacingStr !important;
    word-wrap: break-word !important; 
    overflow-wrap: break-word !important;
    display: block !important;
    overflow: hidden !important;
}

#moby-columns {
    width: 100vw !important;
    height: 100vh !important;
    transition: transform 0.2s ease-out;
    will-change: transform;
    $columnStyle
}

#moby-content {
    padding: 56px 32px 48px 32px !important;
    font-size: ${fontSize.toInt()}% !important;
}

img, svg { max-width: 100% !important; height: auto !important; display: block !important; margin: 10px auto !important; }
p   { margin: 0 0 1em 0 !important; text-align: justify !important; }
h1  { font-size: 1.6em !important; margin: 0.5em 0 !important; }
h2  { font-size: 1.35em !important; margin: 0.5em 0 !important; }
h3  { font-size: 1.15em !important; margin: 0.5em 0 !important; }
a   { color: $linkHex !important; text-decoration: none !important; }
pre, code { white-space: pre-wrap !important; word-break: break-all !important; }
table { max-width: 100% !important; table-layout: fixed !important; }
</style>
<script>
var __mobyW        = 0;
var __mobyTarget   = $virtualPageIndex;
var __mobyReady    = false;
var __mobyVertical = $isVerticalMode;
var __mobySyncLock = false;

function mobyMeasure() {
    var w = window.innerWidth || document.documentElement.clientWidth || 0;
    if (w <= 0) return false;
    __mobyW = w; return true;
}

function mobySync(targetPage) {
    if (__mobySyncLock) return;
    if (!mobyMeasure()) return false;
    
    if (__mobyVertical) {
        if (window.mobyBridge) window.mobyBridge.onPageCountReady(1);
        return true;
    }
    
    __mobySyncLock = true;
    try {
        var el = document.getElementById('moby-columns');
        var scrollW = el.scrollWidth;
        var count = Math.max(1, Math.round(scrollW / __mobyW));
        
        if (window.mobyBridge) window.mobyBridge.onPageCountReady(count);
        
        // Use transform instead of scrollTo for maximum reliability
        el.style.transform = 'translateX(-' + (targetPage * __mobyW) + 'px)';
        __mobyReady = true;
    } finally {
        __mobySyncLock = false;
    }
    return true;
}

function mobyInit(targetPage, attempt) {
    attempt = attempt || 0;
    __mobyTarget = targetPage || 0;
    if (mobySync(targetPage)) {
        setTimeout(function() { mobySync(__mobyTarget); }, 300);
        return;
    }
    if (attempt < 15) setTimeout(function() { mobyInit(targetPage, attempt + 1); }, 100 + attempt * 50);
}

function getXPath(node) {
    if (!node || node === document.body) return '/html/body';
    if (!node.parentNode) return '';
    var count = 0; var siblings = node.parentNode.childNodes;
    for (var i = 0; i < siblings.length; i++) {
        var sib = siblings[i];
        if (sib === node) {
            var name = node.nodeType === 3 ? 'text()' : node.nodeName.toLowerCase();
            return getXPath(node.parentNode) + '/' + name + '[' + (count + 1) + ']';
        }
        if (sib.nodeType === node.nodeType && sib.nodeName === node.nodeName) count++;
    }
    return '';
}
function serializeRange(range) {
    return getXPath(range.startContainer) + ':' + range.startOffset
         + '|' + getXPath(range.endContainer) + ':' + range.endOffset;
}
function resolveXPath(xpath) {
    try { return document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; }
    catch(e) { return null; }
}

function loadHighlights(jsonStr) {
    try {
        var arr = JSON.parse(jsonStr);
        document.body.contentEditable = 'true';
        for (var i = 0; i < arr.length; i++) {
            var parts = arr[i].cfiInfo.split('|');
            var start = parts[0].split(':'); var end = parts[1].split(':');
            var sNode = resolveXPath(start[0]); var eNode = resolveXPath(end[0]);
            if (sNode && eNode) {
                var r = document.createRange();
                r.setStart(sNode, parseInt(start[1])); r.setEnd(eNode, parseInt(end[1]));
                var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(r);
                document.execCommand('hiliteColor', false, arr[i].colorHex);
            }
        }
        window.getSelection().removeAllRanges(); 
        document.body.contentEditable = 'false';
    } catch(e) {}
}

function confirmHighlight(colorHex) {
    document.body.contentEditable = 'true';
    document.execCommand('hiliteColor', false, colorHex);
    document.body.contentEditable = 'false';
    window.getSelection().removeAllRanges();
}

document.addEventListener('selectionchange', function() {
    var sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && sel.toString().trim().length > 0) {
        var range = sel.getRangeAt(0);
        var rect  = range.getBoundingClientRect();
        if (window.mobyBridge) window.mobyBridge.onTextSelected(
            sel.toString(), serializeRange(range), rect.top, rect.left, rect.width, rect.height);
    } else {
        if (window.mobyBridge) window.mobyBridge.onSelectionCleared();
    }
});

var tsX = 0, tsY = 0;
document.addEventListener('touchstart', function(e){ tsX = e.changedTouches[0].clientX; tsY = e.changedTouches[0].clientY; }, {passive: true});
document.addEventListener('touchend', function(e){
    if (document.body.contentEditable === 'true') return;
    if (window.getSelection && window.getSelection().toString().length > 0) return;
    var teX = e.changedTouches[0].clientX; var teY = e.changedTouches[0].clientY;
    
    var dx = teX - tsX;
    var dy = teY - tsY;
    
    // Swipe detection: If movement is mostly horizontal and significant
    if (Math.abs(dx) > 60 && Math.abs(dy) < 40) {
        if (dx < 0) { if (window.mobyBridge) window.mobyBridge.onTapRight(); } // Swipe Left -> Next
        else { if (window.mobyBridge) window.mobyBridge.onTapLeft(); }        // Swipe Right -> Prev
        return;
    }

    // Tap detection: using broader touch area and minimal movement
    if (Math.abs(dx) < 12 && Math.abs(dy) < 12) {
        var w = window.innerWidth;
        if (teX < w * 0.25) { if (window.mobyBridge) window.mobyBridge.onTapLeft(); }
        else if (teX > w * 0.75) { if (window.mobyBridge) window.mobyBridge.onTapRight(); }
        else { if (window.mobyBridge) window.mobyBridge.onTapCenter(); }
    }
}, {passive: false});

window.onload = function() { mobyInit(__mobyTarget, 0); };
if (window.ResizeObserver) {
    new ResizeObserver(function() { mobySync(__mobyTarget); }).observe(document.body);
}
document.addEventListener('load', function(e) { if (e.target.tagName === 'IMG') mobyInit(__mobyTarget, 0); }, true);
</script>
</head>
<body>
    <div id="moby-columns">
        <div id="moby-content">$bodyContent</div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}

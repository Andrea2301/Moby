package com.example.moby.logic.readers.epub

import com.example.moby.ui.screens.ReaderTheme
import java.util.Locale

object EpubHtmlContent {
    fun getCss(
        theme: ReaderTheme,
        fontSize: Float,
        fontFamily: String,
        lineSpacing: Float,
        isVerticalMode: Boolean
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
            :root {
                --moby-bg: $bgHex;
                --moby-text: $textHex;
                --moby-link: $linkHex;
                --moby-font: $fontStack;
                --moby-size: ${fontSize.toInt()}%;
                --moby-spacing: $lineSpacingStr;
            }
            ::-webkit-scrollbar { display: none !important; }
            *, *::before, *::after { box-sizing: border-box !important; }
            html { 
                margin: 0 !important; padding: 0 !important; 
                width: 100vw !important; height: 100vh !important; 
                background-color: var(--moby-bg) !important;
                overflow: hidden !important;
            }
            body { 
                margin: 0 !important; padding: 0 !important;
                width: 100vw !important; height: 100vh !important;
                background-color: var(--moby-bg) !important; 
                color: var(--moby-text) !important;
                font-family: var(--moby-font) !important;
                line-height: var(--moby-spacing) !important;
                word-wrap: break-word !important; 
                overflow-wrap: break-word !important;
                display: block !important;
                overflow: hidden !important;
            }
            #moby-columns {
                min-width: 100vw; 
                height: 100vh !important;
                padding-top: 140px !important;
                padding-bottom: 110px !important;
                box-sizing: border-box !important;
                transition: transform 0.2s ease-out;
                will-change: transform;
                $columnStyle
            }
            #moby-content {
                padding: 0 32px !important;
                font-size: var(--moby-size) !important;
            }
            img, svg { max-width: 100% !important; height: auto !important; display: block !important; margin: 10px auto !important; }
            p   { margin: 0 0 1em 0 !important; text-align: justify !important; }
            a   { color: var(--moby-link) !important; text-decoration: none !important; }
            .moby-highlight {
                background-color: #FFF59D;
                color: inherit;
                border-radius: 2px;
                padding: 1px 0;
            }
            .moby-highlight.green { background-color: #A5D6A7; }
            .moby-highlight.blue  { background-color: #90CAF9; }
            .moby-highlight.pink  { background-color: #F48FB1; }
            
            ::selection {
                background: rgba(126, 200, 227, 0.3);
            }
        """.trimIndent()
    }

    fun getJs(virtualPageIndex: Int, isVerticalMode: Boolean): String {
        return """
            var __mobyW        = 0;
            var __mobyTarget   = $virtualPageIndex;
            var __mobyCount    = 1;
            var __mobyVertical = $isVerticalMode;
            var __mobySyncLock = false;
            var __mobyBoundaryLock = false;

            function mobyFireBoundary(reachedEnd) {
                if (__mobyBoundaryLock) return;
                __mobyBoundaryLock = true;
                if (window.mobyBridge) window.mobyBridge.onChapterBoundary(reachedEnd ? "true" : "false");
                setTimeout(function(){ __mobyBoundaryLock = false; }, 300);
            }

            function mobyMeasure() {
                var w = window.innerWidth || document.documentElement.clientWidth || 0;
                if (w <= 0) return false;
                __mobyW = w;
                var el = document.getElementById('moby-columns');
                if (!el) return false;

                // No forzamos display: none para evitar parpadeos visuales en la recarga
                var scrollW = el.scrollWidth;
                __mobyCount = Math.max(1, Math.round(scrollW / w));  // round en vez de ceil

                if (window.mobyBridge) {
                    window.mobyBridge.onPageCountReady(__mobyCount.toString());
                    
                    var targetChanged = false;
                    if (__mobyTarget === -1) {
                        __mobyTarget = __mobyCount - 1;
                        targetChanged = true;
                    } else if (__mobyTarget >= __mobyCount && __mobyCount > 1) {
                        __mobyTarget = __mobyCount - 1;
                        targetChanged = true;
                    }
                    
                    if (targetChanged && window.mobyBridge) {
                        window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                    }
                }
                return true;
            }

            function mobySync() {
                if (!mobyMeasure()) return;
                var el = document.getElementById('moby-columns');
                if (el) {
                    el.style.transform = 'translateX(-' + (__mobyTarget * __mobyW) + 'px)';
                }
            }

            function mobyNext() {
                if (__mobyVertical) {
                    mobyFireBoundary(true);
                    return;
                }
                mobyMeasure();
                if (__mobyTarget < __mobyCount - 1) {
                    __mobyTarget++;
                    mobySync();
                    if (window.mobyBridge) {
                        window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                    }
                } else {
                    mobyFireBoundary(true);
                }
            }

            function mobyPrev() {
                if (__mobyVertical) {
                    mobyFireBoundary(false);
                    return;
                }
                if (__mobyTarget > 0) {
                    __mobyTarget--;
                    mobySync();
                    if (window.mobyBridge) {
                        window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                    }
                } else {
                    mobyFireBoundary(false);
                }
            }

            function mobyInit(targetPage) {
                __mobyTarget = targetPage;
                mobyMeasure();
                mobySync();
            }

            function mobyApplyHighlight(cfi, colorClass) {
                // Simplified highlight: we find the range by text and context if CFI is just text
                // In a real app we'd use a more robust range-serialization.
                // For now, we'll implement a basic one using window.find or similar if needed,
                // but let's try a wrapping method.
                try {
                    const range = mobyDeserializeRange(cfi);
                    if (range) {
                        const mark = document.createElement('mark');
                        mark.className = 'moby-highlight ' + (colorClass || '');
                        range.surroundContents(mark);
                    }
                } catch(e) { console.error("Highlight error", e); }
            }

            function mobySerializeRange(range) {
                // Representation supporting Text Nodes and Element Nodes
                function getPath(node) {
                    if (node.nodeType === 3) {
                        var index = 0;
                        var sibling = node.previousSibling;
                        while(sibling) { index++; sibling = sibling.previousSibling; }
                        return getPath(node.parentNode) + "|text:" + index;
                    }
                    if (node.id) return '#' + node.id;
                    if (node === document.body) return 'body';
                    var index = 1;
                    var sibling = node.previousSibling;
                    while (sibling) {
                        if (sibling.nodeType === 1 && sibling.tagName === node.tagName) index++;
                        sibling = sibling.previousSibling;
                    }
                    return getPath(node.parentNode) + " > " + node.tagName + ":nth-of-type(" + index + ")";
                }
                return JSON.stringify({
                    startPath: getPath(range.startContainer),
                    startOffset: range.startOffset,
                    endPath: getPath(range.endContainer),
                    endOffset: range.endOffset
                });
            }

            function mobyDeserializeRange(cfi) {
                try {
                    const data = JSON.parse(cfi);
                    function resolvePath(path) {
                        if (path.includes('|text:')) {
                            var parts = path.split('|text:');
                            var parent = document.querySelector(parts[0]);
                            return parent.childNodes[parseInt(parts[1])];
                        } else {
                            return document.querySelector(path);
                        }
                    }
                    const range = document.createRange();
                    range.setStart(resolvePath(data.startPath), data.startOffset);
                    range.setEnd(resolvePath(data.endPath), data.endOffset);
                    return range;
                } catch(e) { return null; }
            }

            (function() {
                var tsX = 0, tsY = 0;
                var hasMoved = false;

                document.addEventListener('touchstart', function(e){ 
                    tsX = e.changedTouches[0].clientX; 
                    tsY = e.changedTouches[0].clientY; 
                    hasMoved = false;
                }, {passive: true});

                document.addEventListener('selectionchange', function() {
                    const selection = window.getSelection();
                    if (selection.rangeCount > 0 && selection.toString().length > 0) {
                        const range = selection.getRangeAt(0);
                        const rect = range.getBoundingClientRect();
                        const cfi = mobySerializeRange(range);
                        if (window.mobyBridge) {
                            window.mobyBridge.onTextSelected(selection.toString(), cfi, rect.top, rect.left, rect.width, rect.height);
                        }
                    } else {
                        if (window.mobyBridge) {
                            window.mobyBridge.onSelectionCleared();
                        }
                    }
                });

                document.addEventListener('touchmove', function(e){
                    var tmX = e.changedTouches[0].clientX;
                    var tmY = e.changedTouches[0].clientY;
                    var dx = tmX - tsX;
                    var dy = tmY - tsY;
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasMoved = true;
                    if (window.getSelection().toString().length > 0) return; // Allow native text selection
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 10 && e.cancelable) {
                        e.preventDefault(); // Prevent WebView from natively scrolling and firing touchcancel
                    }
                }, {passive: false});

                document.addEventListener('touchend', function(e){
                    var teX = e.changedTouches[0].clientX; 
                    var teY = e.changedTouches[0].clientY;
                    var dx = teX - tsX;
                    var dy = teY - tsY;

                    // 1. Detect Swipe (Horizontal) - 20px threshold for better responsiveness
                    if (Math.abs(dx) > 20 && Math.abs(dy) < 80) {
                        if (window.getSelection().toString().length > 0) return; // Ignore swipe if selecting
                        window.getSelection().removeAllRanges();
                        if (dx < 0) mobyNext();
                        else mobyPrev();
                        return;
                    }

                    // 2. Detect Tap (if not moved much)
                    if (!hasMoved || (Math.abs(dx) < 10 && Math.abs(dy) < 10)) {
                        if (window.getSelection().toString().length > 0) {
                            window.getSelection().removeAllRanges();
                            return; // Clear selection on tap, but do not navigate
                        }
                        var w = window.innerWidth;
                        if (teX < w * 0.20) {
                            mobyPrev();
                        } else if (teX > w * 0.80) {
                            mobyNext();
                        } else {
                            if (window.mobyBridge) window.mobyBridge.onTapCenter();
                        }
                    }
                }, {passive: false});
            })();
            
            window.onload = function() { 
                mobyInit(__mobyTarget); 
                
                // Poll for layout changes for 3 seconds
                var lastScrollW = 0;
                var lastInnerW = 0;
                var polls = 0;
                var interval = setInterval(function() {
                    var el = document.getElementById('moby-columns');
                    var w = window.innerWidth || 0;
                    if (el) {
                        if (el.scrollWidth !== lastScrollW || w !== lastInnerW) {
                            lastScrollW = el.scrollWidth;
                            lastInnerW = w;
                            mobyMeasure();
                            mobySync();
                        }
                    }
                    if (++polls > 30) clearInterval(interval); // 30 * 100ms = 3 seconds
                }, 100);
            };
        """.trimIndent()
    }

    fun build(
        bodyContent: String,
        theme: ReaderTheme,
        fontSize: Float,
        fontFamily: String,
        lineSpacing: Float,
        isVerticalMode: Boolean,
        virtualPageIndex: Int
    ): String {
        val css = getCss(theme, fontSize, fontFamily, lineSpacing, isVerticalMode)
        val js  = getJs(virtualPageIndex, isVerticalMode)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                <style id="moby-style-tag">$css</style>
                <script>$js</script>
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

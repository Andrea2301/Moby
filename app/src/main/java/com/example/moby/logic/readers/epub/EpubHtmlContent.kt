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
        """.trimIndent()
    }

    fun getJs(virtualPageIndex: Int, isVerticalMode: Boolean): String {
        return """
            var __mobyW        = 0;
            var __mobyTarget   = $virtualPageIndex;
            var __mobyCount    = 1;
            var __mobyVertical = $isVerticalMode;

            function mobyMeasure() {
                var w = window.innerWidth || document.documentElement.clientWidth || 0;
                if (w <= 0) return false;
                __mobyW = w;
                var el = document.getElementById('moby-columns');
                if (el) {
                    __mobyCount = Math.max(1, Math.ceil(el.scrollWidth / w));
                    if (window.mobyBridge) window.mobyBridge.onPageCountReady(__mobyCount);
                }
                return true;
            }

            function mobySync() {
                if (!mobyMeasure()) return;
                var el = document.getElementById('moby-columns');
                if (el) el.style.transform = 'translateX(-' + (__mobyTarget * __mobyW) + 'px)';
            }

            function mobyNext() {
                if (__mobyVertical) {
                    if (window.mobyBridge) window.mobyBridge.onChapterBoundary(true);
                    return;
                }
                mobyMeasure();
                if (__mobyTarget < __mobyCount - 1) {
                    __mobyTarget++;
                    mobySync();
                } else {
                    if (window.mobyBridge) window.mobyBridge.onChapterBoundary(true);
                }
            }

            function mobyPrev() {
                if (__mobyVertical) {
                    if (window.mobyBridge) window.mobyBridge.onChapterBoundary(false);
                    return;
                }
                if (__mobyTarget > 0) {
                    __mobyTarget--;
                    mobySync();
                } else {
                    if (window.mobyBridge) window.mobyBridge.onChapterBoundary(false);
                }
            }

            function mobyInit(targetPage) {
                __mobyTarget = targetPage;
                mobySync();
                setTimeout(mobySync, 100);
            }

            (function() {
                var tsX = 0, tsY = 0;
                document.addEventListener('touchstart', function(e){ tsX = e.changedTouches[0].clientX; tsY = e.changedTouches[0].clientY; }, {passive: true});
                document.addEventListener('touchend', function(e){
                    var teX = e.changedTouches[0].clientX; var teY = e.changedTouches[0].clientY;
                    if (Math.abs(teX - tsX) < 15 && Math.abs(teY - tsY) < 15) {
                        var w = window.innerWidth;
                        if (teX < w * 0.25) mobyPrev();
                        else if (teX > w * 0.75) mobyNext();
                        else if (window.mobyBridge) window.mobyBridge.onTapCenter();
                    }
                }, {passive: false});
            })();
            
            window.onload = function() { mobyInit(__mobyTarget); };
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

package com.example.moby.logic.readers.epub

import com.example.moby.ui.screens.ReaderTheme

object EpubHtmlContent {

    private fun getCss(theme: ReaderTheme, fontSize: Float, fontFamily: String, lineSpacing: Float, isVertical: Boolean): String {
        val backgroundColor = when (theme) {
            ReaderTheme.ARRECIFE -> "#F8F9FA"
            ReaderTheme.ABISAL -> "#0F172A"
            ReaderTheme.ONYX -> "#000000"
            ReaderTheme.CRETA -> "#F2F2F2"
            ReaderTheme.PAPIRUS -> "#EFDEC1"
        }
        val textColor = when (theme) {
            ReaderTheme.ARRECIFE -> "#1E293B"
            ReaderTheme.ABISAL -> "#E2E8F0"
            ReaderTheme.ONYX -> "#FFFFFF"
            ReaderTheme.CRETA -> "#333333"
            ReaderTheme.PAPIRUS -> "#4E342E"
        }

        val topMargin = "calc(110px + env(safe-area-inset-top))"
        val bottomMargin = "80px"

        return """
            body {
                background-color: $backgroundColor;
                color: $textColor;
                font-family: $fontFamily, sans-serif;
                font-size: ${fontSize}%;
                line-height: $lineSpacing;
                margin: 0;
                padding: 0;
                overflow: hidden;
                height: 100vh;
                width: 100vw;
                -webkit-user-select: text;
                user-select: text;
                -webkit-tap-highlight-color: transparent;
            }

            #moby-columns {
                display: block;
                height: calc(100vh - $topMargin - $bottomMargin);
                margin-top: $topMargin;
                width: 100vw;
                column-width: 100vw;
                column-gap: 0;
                column-fill: auto;
                transition: transform 0.3s ease-out;
            }

            #moby-content {
                padding: 0 30px;
                box-sizing: border-box;
                height: 100%;
            }

            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 20px auto;
            }

            p { text-align: justify; margin-bottom: 1.2em; }

            h1, h2, h3 {
                color: ${if (theme == ReaderTheme.PAPIRUS) "#3E2723" else textColor};
            }

            #moby-bookmark-ribbon {
                position: fixed;
                top: 0;
                right: 20px;
                width: 32px;
                height: 52px;
                background: linear-gradient(135deg, #FF5252 0%, #D32F2F 100%);
                clip-path: polygon(0% 0%, 100% 0%, 100% 100%, 50% 85%, 0% 100%);
                z-index: 2000;
                transform: translateY(-100%);
                transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            }

            #moby-bookmark-ribbon.visible { transform: translateY(0); }
            
            .moby-highlight {
                background-color: rgba(255, 235, 59, 0.4);
                border-bottom: 2px solid #FFD600;
            }
        """.trimIndent()
    }

    private fun getJs(targetPage: Int, isVertical: Boolean): String {
        return """
            var __mobyTarget = $targetPage;
            var __mobyW = 0;
            var __mobyCount = 0;
            var __mobyNavLock = false;
            var __mobyBookmarks = [];

            function mobyMeasure() {
                var w = window.innerWidth;
                if (w <= 0) return false;
                __mobyW = w;
                var el = document.getElementById('moby-columns');
                if (!el) return false;
                __mobyCount = Math.max(1, Math.round(el.scrollWidth / w));
                if (window.mobyBridge) window.mobyBridge.onPageCountReady(__mobyCount.toString());
                return true;
            }

            function mobySync() {
                if (!mobyMeasure()) return;
                var el = document.getElementById('moby-columns');
                if (el) {
                    if (__mobyTarget === -1) {
                        __mobyTarget = Math.max(0, __mobyCount - 1);
                        if (window.mobyBridge) window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                    }
                    el.style.transform = 'translateX(-' + (__mobyTarget * __mobyW) + 'px)';
                }
                mobyCheckBookmarks();
            }

            // MOTOR DE OFFSETS (KINDLE STYLE)
            function getAbsoluteOffset(node, offset) {
                var range = document.createRange();
                var contentEl = document.getElementById('moby-content');
                if (!contentEl) return 0;
                range.selectNodeContents(contentEl);
                range.setEnd(node, offset);
                return range.toString().length;
            }

            window.mobyUpdateBookmarks = function(offsetsJson) {
                try {
                    __mobyBookmarks = JSON.parse(offsetsJson).map(Number);
                    mobyCheckBookmarks();
                } catch(e) {}
            };

            window.mobyApplyHighlight = function(cfi, color) {
                try {
                    var range = mobyDeserializeRange(cfi);
                    if (range) {
                        var mark = document.createElement('mark');
                        mark.className = 'moby-highlight';
                        if (color) {
                            mark.style.backgroundColor = color + '66';
                            mark.style.borderBottom = '2px solid ' + color;
                        }
                        range.surroundContents(mark);
                    }
                } catch(e) {}
            };

            function mobyCheckBookmarks() {
                var ribbon = document.getElementById('moby-bookmark-ribbon');
                if (!ribbon) return;
                
                var startRange = document.caretRangeFromPoint(30, 150);
                var endRange = document.caretRangeFromPoint(__mobyW - 30, window.innerHeight - 100);
                
                if (!startRange || !endRange) {
                    ribbon.classList.remove('visible');
                    return;
                }

                var startOffset = getAbsoluteOffset(startRange.startContainer, startRange.startOffset);
                var endOffset = getAbsoluteOffset(endRange.startContainer, endRange.startOffset);
                
                var isVisible = __mobyBookmarks.some(function(off) {
                    return off >= startOffset && off <= endOffset;
                });

                if (isVisible) ribbon.classList.add('visible');
                else ribbon.classList.remove('visible');
            }

            window.mobyRequestToggleBookmark = function() {
                var range = document.caretRangeFromPoint(60, 150);
                if (range && window.mobyBridge) {
                    var offset = getAbsoluteOffset(range.startContainer, range.startOffset);
                    window.mobyBridge.onBookmarkToggled(offset.toString());
                }
            };

            function mobyNext() {
                if (__mobyNavLock) return;
                __mobyNavLock = true;
                setTimeout(function(){ __mobyNavLock = false; }, 300);
                mobyMeasure();
                if (__mobyTarget < __mobyCount - 1) {
                    __mobyTarget++; mobySync();
                    if (window.mobyBridge) window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                } else if (window.mobyBridge) window.mobyBridge.onChapterBoundary("true");
            }

            function mobyPrev() {
                if (__mobyNavLock) return;
                __mobyNavLock = true;
                setTimeout(function(){ __mobyNavLock = false; }, 300);
                if (__mobyTarget > 0) {
                    __mobyTarget--; mobySync();
                    if (window.mobyBridge) window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
                } else if (window.mobyBridge) window.mobyBridge.onChapterBoundary("false");
            }

            function mobyInit(targetPage) {
                __mobyTarget = targetPage;
                var el = document.getElementById('moby-columns');
                if (el) el.style.transition = 'none';
                mobySync();
                setTimeout(function() { if (el) el.style.transition = 'transform 0.3s ease-out'; }, 50);
            }

            function mobySerializeRange(range) {
                function getPath(node) {
                    if (node.nodeType === 3) {
                        var index = 0; var sibling = node.previousSibling;
                        while(sibling) { index++; sibling = sibling.previousSibling; }
                        return getPath(node.parentNode) + "|text:" + index;
                    }
                    if (node.id) return '#' + node.id;
                    if (node === document.body) return 'body';
                    var index = 1; var sibling = node.previousSibling;
                    while (sibling) {
                        if (sibling.nodeType === 1 && sibling.tagName === node.tagName) index++;
                        sibling = sibling.previousSibling;
                    }
                    return getPath(node.parentNode) + " > " + node.tagName + ":nth-of-type(" + index + ")";
                }
                return JSON.stringify({
                    startPath: getPath(range.startContainer), startOffset: range.startOffset,
                    endPath: getPath(range.endContainer), endOffset: range.endOffset
                });
            }

            function mobyDeserializeRange(cfi) {
                try {
                    var data = JSON.parse(cfi);
                    function resolvePath(path) {
                        if (path.includes('|text:')) {
                            var parts = path.split('|text:');
                            var parent = document.querySelector(parts[0]);
                            if (!parent) return null;
                            return parent.childNodes[parseInt(parts[1])];
                        } else return document.querySelector(path);
                    }
                    var sn = resolvePath(data.startPath);
                    var en = resolvePath(data.endPath);
                    if (!sn || !en) return null;
                    var r = document.createRange();
                    r.setStart(sn, data.startOffset);
                    r.setEnd(en, data.endOffset);
                    return r;
                } catch(e) { return null; }
            }

            (function() {
                var tsX = 0, tsY = 0, hasMoved = false;
                document.addEventListener('touchstart', function(e){ tsX = e.changedTouches[0].clientX; tsY = e.changedTouches[0].clientY; hasMoved = false; }, {passive: true});
                document.addEventListener('touchmove', function(e){
                    var dx = e.changedTouches[0].clientX - tsX;
                    var dy = e.changedTouches[0].clientY - tsY;
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasMoved = true;
                }, {passive: true});
                document.addEventListener('touchend', function(e){
                    var dx = e.changedTouches[0].clientX - tsX, dy = e.changedTouches[0].clientY - tsY;
                    var w = window.innerWidth;
                    
                    // CORNER TAP (Esquina derecha alta)
                    if (!hasMoved && e.changedTouches[0].clientX > w * 0.75 && e.changedTouches[0].clientY < 200) {
                        window.mobyRequestToggleBookmark();
                        return;
                    }

                    if (Math.abs(dx) > 30 && Math.abs(dy) < 100) {
                        if (window.getSelection().toString().length > 0) return;
                        if (dx < 0) mobyNext(); else mobyPrev();
                    } else if (!hasMoved) {
                        if (window.getSelection().toString().length > 0) return;
                        if (e.changedTouches[0].clientX < w * 0.20) mobyPrev();
                        else if (e.changedTouches[0].clientX > w * 0.80) mobyNext();
                        else if (window.mobyBridge) window.mobyBridge.onTapCenter();
                    }
                }, {passive: false});

                document.addEventListener('selectionchange', function() {
                    var selection = window.getSelection();
                    if (selection.rangeCount > 0 && selection.toString().length > 0) {
                        var range = selection.getRangeAt(0);
                        var rect = range.getBoundingClientRect();
                        var cfi = mobySerializeRange(range);
                        if (window.mobyBridge) window.mobyBridge.onTextSelected(selection.toString(), cfi, rect.top, rect.left, rect.width, rect.height);
                    } else if (window.mobyBridge) {
                        setTimeout(function() {
                            if (window.getSelection().toString().length === 0) {
                                window.mobyBridge.onSelectionCleared();
                            }
                        }, 100);
                    }
                });
            })();
            
            window.onload = function() { 
                mobyInit(__mobyTarget); 
                setTimeout(mobySync, 200); 
                setInterval(mobySync, 3000);
            };
        """.trimIndent()
    }

    fun build(bodyContent: String, theme: ReaderTheme, fontSize: Float, fontFamily: String, lineSpacing: Float, isVerticalMode: Boolean, virtualPageIndex: Int): String {
        val css = getCss(theme, fontSize, fontFamily, lineSpacing, isVerticalMode)
        val js  = getJs(virtualPageIndex, isVerticalMode)
        return """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover"><style>$css</style><script>$js</script></head><body><div id="moby-bookmark-ribbon" onclick="window.mobyRequestToggleBookmark()"></div><div id="moby-columns"><div id="moby-content">$bodyContent</div></div></body></html>""".trimIndent()
    }
}

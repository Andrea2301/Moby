package com.example.moby.logic.readers

import com.example.moby.ui.screens.ReaderTheme
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.absoluteValue

// ─────────────────────────────────────────────────────────────────────────────
// ZIP CACHE
// ─────────────────────────────────────────────────────────────────────────────
private object ZipCache {
    private var cachedPath: String? = null
    private var cachedZip: ZipFile? = null

    @Synchronized
    fun readEntry(filePath: String, entryPath: String): ByteArray? {
        return try {
            if (cachedPath != filePath || cachedZip == null) {
                cachedZip?.close()
                cachedZip = ZipFile(File(filePath))
                cachedPath = filePath
            }
            val entry = cachedZip?.getEntry(entryPath) ?: return null
            cachedZip?.getInputStream(entry)?.readBytes()
        } catch (e: Exception) {
            cachedZip?.close()
            cachedZip = null; cachedPath = null; null
        }
    }

    @Synchronized
    fun close() { cachedZip?.close(); cachedZip = null; cachedPath = null }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS DE TEMA
// ─────────────────────────────────────────────────────────────────────────────
private fun ReaderTheme.toColor() = when (this) {
    ReaderTheme.ARRECIFE -> Color(0xFFF8F9FA)
    ReaderTheme.CRETA    -> Color(0xFFF4ECD8)
    ReaderTheme.PAPIRUS  -> Color(0xFFD2D2D2)
    ReaderTheme.ABISAL   -> Color(0xFF011627)
}
private fun ReaderTheme.toBgHex() = when (this) {
    ReaderTheme.ARRECIFE -> "#F8F9FA"
    ReaderTheme.CRETA    -> "#F4ECD8"
    ReaderTheme.PAPIRUS  -> "#D2D2D2"
    ReaderTheme.ABISAL   -> "#011627"
}
private fun ReaderTheme.toTextHex() = when (this) {
    ReaderTheme.ARRECIFE -> "#2C3E50"
    ReaderTheme.CRETA    -> "#423425"
    ReaderTheme.PAPIRUS  -> "#1A1A1A"
    ReaderTheme.ABISAL   -> "#D0D0D0"
}
private fun ReaderTheme.toLinkHex() = when (this) {
    ReaderTheme.ABISAL -> "#7EC8E3"
    else               -> "#2980B9"
}

// ─────────────────────────────────────────────────────────────────────────────
// JAVASCRIPT BRIDGE
// ─────────────────────────────────────────────────────────────────────────────
class EpubJavascriptBridge(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onVirtualPageCountReady: (Int) -> Unit,
    private val onTextSelectedRaw: (String, String, Float, Float) -> Unit,
    private val onSelectionClearedRaw: () -> Unit,
    private val onLeftTap: () -> Unit,
    private val onRightTap: () -> Unit,
    private val onCenterTap: () -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onPageCountReady(count: Int) { scope.launch(kotlinx.coroutines.Dispatchers.Main) { onVirtualPageCountReady(count) } }
    @android.webkit.JavascriptInterface
    fun onTextSelected(text: String, cfi: String, top: Float, left: Float, w: Float, h: Float) {
        scope.launch(kotlinx.coroutines.Dispatchers.Main) { onTextSelectedRaw(text, cfi, left, top) }
    }
    @android.webkit.JavascriptInterface
    fun onSelectionCleared() { scope.launch(kotlinx.coroutines.Dispatchers.Main) { onSelectionClearedRaw() } }
    @android.webkit.JavascriptInterface
    fun onTapLeft() { scope.launch(kotlinx.coroutines.Dispatchers.Main) { onLeftTap() } }
    @android.webkit.JavascriptInterface
    fun onTapRight() { scope.launch(kotlinx.coroutines.Dispatchers.Main) { onRightTap() } }
    @android.webkit.JavascriptInterface
    fun onTapCenter() { scope.launch(kotlinx.coroutines.Dispatchers.Main) { onCenterTap() } }
}

// ─────────────────────────────────────────────────────────────────────────────
// EPUB READER COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EpubReaderComponent(
    publicationId: String,
    filePath: String,
    initialChapter: Int,
    onChapterChanged: (Int) -> Unit,
    onVirtualPageChanged: (Int, Int) -> Unit = { _, _ -> },
    onTotalChaptersReady: (Int) -> Unit,
    fontSize: Float = 100f,
    fontFamily: String = "Serif",
    lineSpacing: Float = 1.65f,
    isVerticalMode: Boolean,
    theme: ReaderTheme,
    onCenterTap: () -> Unit
) {
    var chapters by remember { mutableStateOf<List<String>>(emptyList()) }
    var opfDir by remember { mutableStateOf("") }
    var fileLoadError by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { com.example.moby.data.db.MobyDatabase.getDatabase(context).publicationDao() }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val zip = ZipFile(File(filePath))
                val ce = zip.getEntry("META-INF/container.xml") ?: throw Exception("No container.xml")
                val cxml = zip.getInputStream(ce).bufferedReader().readText()
                val opfP = """<rootfile[^>]+full-path="([^"]+)"""".toRegex()
                    .find(cxml)?.groupValues?.get(1) ?: throw Exception("No OPF path")
                opfDir = if (opfP.contains("/")) opfP.substringBeforeLast("/") + "/" else ""
                val opfE = zip.getEntry(opfP) ?: throw Exception("OPF not found")
                val opfX = zip.getInputStream(opfE).bufferedReader().readText()
                val manifestMap = mutableMapOf<String, String>()
                val iRex = """<item[^>]+id="([^"]+)"[^>]+href="([^"]+)"|<item[^>]+href="([^"]+)"[^>]+id="([^"]+)"""".toRegex()
                for (m in iRex.findAll(opfX)) {
                    val id1 = m.groupValues[1]; val h1 = m.groupValues[2]
                    val h2 = m.groupValues[3]; val id2 = m.groupValues[4]
                    if (id1.isNotEmpty()) manifestMap[id1] = h1
                    if (id2.isNotEmpty()) manifestMap[id2] = h2
                }
                val spineX = """<spine[^>]*>(.*?)</spine>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(opfX)?.groupValues?.get(1) ?: throw Exception("No spine found")
                val chapterHs = """<itemref[^>]+idref="([^"]+)"""".toRegex()
                    .findAll(spineX).mapNotNull { manifestMap[it.groupValues[1]] }.toList()
                zip.close()
                withContext(Dispatchers.Main) { chapters = chapterHs; onTotalChaptersReady(chapterHs.size) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { fileLoadError = true }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { ZipCache.close() } }

    if (fileLoadError || chapters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (fileLoadError) Text("Error cargando el libro.") else CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialChapter.coerceIn(0, chapters.size - 1),
        pageCount = { chapters.size }
    )
    val scope = rememberCoroutineScope()
    var virtualPageIndex by remember { mutableIntStateOf(0) }
    val chapterPageCounts = remember { mutableStateMapOf<Int, Int>() }
    val virtualPageCount  = chapterPageCounts[pagerState.currentPage] ?: 1
    val isChapterReady    = chapterPageCounts.containsKey(pagerState.currentPage)

    // FIX: iniciar en TRUE para que el pager pueda recibir el primer swipe
    var isPagingSwipeAllowed by remember { mutableStateOf(true) }

    // Limpiar al cambiar configuración visual
    LaunchedEffect(fontSize, fontFamily, lineSpacing, theme, isVerticalMode) {
        chapterPageCounts.clear()
        virtualPageIndex = 0
    }

    // Aterrizar en última página al navegar hacia atrás
    var previousChapter by remember { mutableIntStateOf(pagerState.currentPage) }
    var landOnLastPage  by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val goingBack = pagerState.currentPage < previousChapter
        previousChapter = pagerState.currentPage
        landOnLastPage  = goingBack
        virtualPageIndex = 0
        onChapterChanged(pagerState.currentPage)
    }

    LaunchedEffect(isChapterReady, virtualPageCount) {
        if (isChapterReady && landOnLastPage) {
            virtualPageIndex = maxOf(0, virtualPageCount - 1)
            landOnLastPage = false
        }
    }

    // FIX: navegar desde el menú de capítulos
    // Cuando initialChapter cambia externamente (ej: el usuario pulsa un capítulo
    // en el índice), saltamos sin animación para que sea inmediato y confiable.
    LaunchedEffect(initialChapter) {
        val target = initialChapter.coerceIn(0, chapters.size - 1)
        if (pagerState.currentPage != target) {
            virtualPageIndex = 0
            landOnLastPage   = false
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(virtualPageIndex, virtualPageCount) {
        onVirtualPageChanged(virtualPageIndex, virtualPageCount)
    }

    // Fuentes únicas de navegación — usadas tanto por swipe como por taps del JS
    val goLeft: () -> Unit = {
        if (isChapterReady) {
            if (!isVerticalMode) {
                if (virtualPageIndex > 0) virtualPageIndex--
                else if (pagerState.currentPage > 0)
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            } else {
                if (pagerState.currentPage > 0)
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        }
    }
    val goRight: () -> Unit = {
        if (isChapterReady) {
            if (!isVerticalMode) {
                if (virtualPageIndex < virtualPageCount - 1) virtualPageIndex++
                else if (pagerState.currentPage < chapters.size - 1)
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else {
                if (pagerState.currentPage < chapters.size - 1)
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
    }

    val bg = theme.toColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isVerticalMode) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> isPagingSwipeAllowed = true
                            PointerEventType.Move  -> {
                                val change = event.changes.firstOrNull() ?: continue
                                val delta  = change.position - change.previousPosition
                                val dx = kotlin.math.abs(delta.x)
                                val dy = kotlin.math.abs(delta.y)
                                // Deshabilitar swipe solo si el gesto opuesto es muy dominante
                                if (!isVerticalMode && dy > dx * 3f) isPagingSwipeAllowed = false
                                if (isVerticalMode  && dx > dy * 3f) isPagingSwipeAllowed = false
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        val pagerModifier = Modifier.fillMaxSize().background(bg)

        if (!isVerticalMode) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = isPagingSwipeAllowed,
                modifier = pagerModifier,
                beyondViewportPageCount = 1
            ) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                    val abs = offset.absoluteValue.coerceIn(0f, 1f)
                    scaleX = lerp(0.88f, 1f, 1f - abs)
                    scaleY = lerp(0.88f, 1f, 1f - abs)
                    alpha  = lerp(0.5f,  1f, 1f - abs)
                }) {
                    EpubChapterRender(
                        publicationId = publicationId, dao = dao,
                        filePath = filePath,
                        internalPath = opfDir + android.net.Uri.decode(chapters[page]),
                        theme = theme, fontSize = fontSize,
                        fontFamily = fontFamily, lineSpacing = lineSpacing,
                        isVerticalMode = false,
                        virtualPageIndex = if (page == pagerState.currentPage) virtualPageIndex else 0,
                        onLeftTap = goLeft, onRightTap = goRight, onCenterTap = onCenterTap,
                        onVirtualPageCountReady = { count -> chapterPageCounts[page] = count }
                    )
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                userScrollEnabled = isPagingSwipeAllowed,
                modifier = pagerModifier
            ) { page ->
                EpubChapterRender(
                    publicationId = publicationId, dao = dao,
                    filePath = filePath,
                    internalPath = opfDir + android.net.Uri.decode(chapters[page]),
                    theme = theme, fontSize = fontSize,
                    fontFamily = fontFamily, lineSpacing = lineSpacing,
                    isVerticalMode = true, virtualPageIndex = 0,
                    onLeftTap = goLeft, onRightTap = goRight, onCenterTap = onCenterTap,
                    onVirtualPageCountReady = { chapterPageCounts[page] = 1 }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EPUB CHAPTER RENDER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EpubChapterRender(
    publicationId: String,
    dao: com.example.moby.data.db.PublicationDao,
    filePath: String,
    internalPath: String,
    theme: ReaderTheme,
    fontSize: Float,
    fontFamily: String,
    lineSpacing: Float,
    isVerticalMode: Boolean,
    virtualPageIndex: Int,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    onCenterTap: () -> Unit,
    onVirtualPageCountReady: (Int) -> Unit
) {
    data class SelectionInfo(val text: String, val cfi: String, val x: Float, val y: Float)

    var activeSelection by remember { mutableStateOf<SelectionInfo?>(null) }
    var htmlContent     by remember { mutableStateOf<String?>(null) }
    val webViewRef      = remember { mutableStateOf<WebView?>(null) }
    var isPageReady     by remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()

    // Scroll a página virtual — solo cuando la página está lista
    LaunchedEffect(virtualPageIndex, isPageReady) {
        if (!isVerticalMode && isPageReady) {
            delay(60)
            webViewRef.value?.evaluateJavascript(
                """(function(){
                    var w = window.__mobyW || document.documentElement.clientWidth || window.innerWidth;
                    if (w > 0) window.scrollTo(${virtualPageIndex} * w, 0);
                })();""", null
            )
        }
    }

    // Construir HTML al cambiar capítulo o configuración
    LaunchedEffect(internalPath, isVerticalMode, fontSize, fontFamily, lineSpacing, theme) {
        delay(250) // Debounce rapid slider changes to prevent WebView reload thrashing
        isPageReady = false
        withContext(Dispatchers.IO) {
            try {
                val z = ZipFile(File(filePath))
                val e = z.getEntry(internalPath)
                if (e != null) {
                    val raw = z.getInputStream(e).bufferedReader().readText(); z.close()
                    val bodyContent = Regex("(?si)<body[^>]*>(.*?)</body>").find(raw)?.groupValues?.get(1) ?: raw

                    val bgHex    = theme.toBgHex()
                    val textHex  = theme.toTextHex()
                    val linkHex  = theme.toLinkHex()
                    val fontStack = if (fontFamily == "Sans") "sans-serif" else "Georgia, serif"
                    val layoutCss = if (isVerticalMode) """
                        overflow-y: auto !important; height: auto !important; min-height: 100% !important;
                        display: block !important; width: 100% !important; padding: 48px 28px 96px 28px !important;
                    """ else """
                        width: 100% !important; height: 100% !important; overflow: hidden !important;
                        display: block !important; -webkit-column-width: 100% !important; column-width: 100% !important;
                        -webkit-column-gap: 0 !important; column-fill: auto !important; padding: 48px 28px 96px 28px !important;
                    """

                    // JS escrito en ES5 puro — compatible con WebView API 21+
                    val html = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
<style>
::-webkit-scrollbar { display: none !important; }
*, *::before, *::after { box-sizing: border-box !important; }
html { margin:0 !important; padding:0 !important; width:100% !important; height:100% !important;
       background-color:$bgHex !important; -webkit-text-size-adjust:100% !important; text-size-adjust:100% !important; }
body { margin:0 !important; width:100% !important;
       background-color:$bgHex !important; color:$textHex !important;
       font-family:$fontStack !important; font-size:${fontSize.toInt()}% !important;
       line-height:${String.format(java.util.Locale.US, "%.2f", lineSpacing)} !important;
       word-wrap:break-word !important; overflow-wrap:break-word !important;
       $layoutCss }
img, svg { max-width:100% !important; height:auto !important; display:block !important; margin:10px auto !important; }
p   { margin:0 0 1em 0 !important; text-align:justify !important; width: 100% !important; }
h1  { font-size:1.6em !important; margin:0.5em 0 !important; }
h2  { font-size:1.35em !important; margin:0.5em 0 !important; }
h3  { font-size:1.15em !important; margin:0.5em 0 !important; }
a   { color:$linkHex !important; text-decoration:none !important; }
pre, code { white-space:pre-wrap !important; word-break:break-all !important; }
table { max-width:100% !important; table-layout:fixed !important; }
</style>
<script>
var __mobyW        = 0;
var __mobyTarget   = 0;
var __mobyReady    = false;
var __mobyVertical = ${isVerticalMode};

function mobyMeasure() {
    var w = document.documentElement.clientWidth || window.innerWidth || 0;
    if (w <= 0) return false;
    __mobyW = w; return true;
}

function mobySync(targetPage) {
    if (!mobyMeasure()) return false;
    if (__mobyVertical) {
        if (window.mobyBridge) window.mobyBridge.onPageCountReady(1);
        return true;
    }
    var reflow   = document.body.offsetHeight;
    var scrollW  = document.documentElement.scrollWidth || document.body.scrollWidth || __mobyW;
    var count    = Math.max(1, Math.round(scrollW / __mobyW));
    if (window.mobyBridge) window.mobyBridge.onPageCountReady(count);
    if (targetPage > 0) window.scrollTo(targetPage * __mobyW, 0);
    __mobyReady = true; return true;
}

function mobyInit(targetPage, attempt) {
    attempt = attempt || 0;
    __mobyTarget = targetPage || 0;
    if (mobySync(targetPage)) return;
    if (attempt < 12) setTimeout(function() { mobyInit(targetPage, attempt + 1); }, 80 + attempt * 60);
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

// Selección de texto
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

// Taps de navegación reactivos a nivel de kernel móvil (evita retrasos de click)
var tsX = 0, tsY = 0;
document.addEventListener('touchstart', function(e){
    tsX = e.changedTouches[0].clientX;
    tsY = e.changedTouches[0].clientY;
}, {passive: true});
document.addEventListener('touchend', function(e){
    if (document.body.contentEditable === 'true') return;
    if (window.getSelection && window.getSelection().toString().length > 0) return;
    var teX = e.changedTouches[0].clientX;
    var teY = e.changedTouches[0].clientY;
    if (Math.abs(teX - tsX) < 15 && Math.abs(teY - tsY) < 15) {
        var w = window.innerWidth || document.documentElement.clientWidth;
        if      (teX < w * 0.25) { if (window.mobyBridge) window.mobyBridge.onTapLeft(); }
        else if (teX > w * 0.75) { if (window.mobyBridge) window.mobyBridge.onTapRight(); }
        else                     { if (window.mobyBridge) window.mobyBridge.onTapCenter(); }
    }
}, {passive: false});

window.onload = function() { setTimeout(function() { mobyInit(__mobyTarget, 0); }, 150); };
document.addEventListener('DOMContentLoaded', function() {
    setTimeout(function() { if (!__mobyReady) mobyInit(__mobyTarget, 0); }, 80);
});
</script>
</head>
<body>$bodyContent</body>
</html>
                    """.trimIndent()
                    withContext(Dispatchers.Main) { htmlContent = html }
                } else { z.close() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val bg = theme.toColor()

    Box(modifier = Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = internalPath, // FIX: Solo animar al cambiar de capítulo, no al cambiar fuente/tema
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            label = "EpubFade"
        ) { chapterPath ->
            val content = htmlContent
            if (content != null) {
                val chapterDir = chapterPath.substringBeforeLast("/", "")
                val baseUrl    = if (chapterDir.isNotEmpty()) "moby-epub://book/$chapterDir/" else "moby-epub://book/"

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled   = isVerticalMode
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled    = true
                                domStorageEnabled    = true
                                allowContentAccess   = true
                                allowFileAccess      = false
                                textZoom             = 100
                                useWideViewPort      = false
                                loadWithOverviewMode = false
                                setSupportZoom(false)
                                builtInZoomControls  = false
                                displayZoomControls  = false
                                cacheMode            = android.webkit.WebSettings.LOAD_NO_CACHE
                                mixedContentMode     = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            val bridge = EpubJavascriptBridge(
                                scope = scope,
                                onVirtualPageCountReady = onVirtualPageCountReady,
                                onTextSelectedRaw = { text, cfi, left, top ->
                                    val d = ctx.resources.displayMetrics.density
                                    activeSelection = SelectionInfo(text, cfi, left * d, top * d)
                                },
                                onSelectionClearedRaw = { activeSelection = null },
                                onLeftTap = onLeftTap,
                                onRightTap = onRightTap,
                                onCenterTap = onCenterTap
                            )
                            addJavascriptInterface(bridge, "mobyBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // FIX: mobyInit con retry — no mobySync directo
                                    view?.evaluateJavascript("mobyInit($virtualPageIndex, 0);", null)
                                    isPageReady = true
                                    // Cargar highlights
                                    scope.launch(Dispatchers.IO) {
                                        val ann = dao.getAnnotationsForChapter(publicationId, internalPath)
                                        if (ann.isNotEmpty()) {
                                            val json = buildString {
                                                append("[")
                                                ann.forEachIndexed { i, a ->
                                                    append("""{"id":"${a.id}","cfiInfo":"${a.cfiInfo}","colorHex":"${a.colorHex}"}""")
                                                    if (i < ann.size - 1) append(",")
                                                }
                                                append("]")
                                            }
                                            withContext(Dispatchers.Main) {
                                                view?.evaluateJavascript("loadHighlights('$json');", null)
                                            }
                                        }
                                    }
                                }
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val uri = request?.url ?: return null
                                    if (uri.scheme != "moby-epub") return null
                                    val decoded = android.net.Uri.decode(uri.path?.removePrefix("/") ?: return null)
                                    val bytes = ZipCache.readEntry(filePath, decoded) ?: run {
                                        val cDir = internalPath.substringBeforeLast("/", "")
                                        val rel  = if (cDir.isNotEmpty()) "$cDir/$decoded" else decoded
                                        ZipCache.readEntry(filePath, rel)
                                    } ?: return null
                                    val ext  = decoded.substringAfterLast('.', "").lowercase()
                                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                                        ?: when (ext) {
                                            "css"         -> "text/css"
                                            "js"          -> "application/javascript"
                                            "svg"         -> "image/svg+xml"
                                            "jpg","jpeg"  -> "image/jpeg"
                                            "png"         -> "image/png"
                                            "gif"         -> "image/gif"
                                            "webp"        -> "image/webp"
                                            "ttf"         -> "font/ttf"
                                            "otf"         -> "font/otf"
                                            "woff"        -> "font/woff"
                                            "woff2"       -> "font/woff2"
                                            else          -> "application/octet-stream"
                                        }
                                    return WebResourceResponse(mime, "UTF-8", bytes.inputStream())
                                }
                            }
                        }
                    },
                    update = { view ->
                        // FIX: tag usa el hashCode del contenido para recargar cuando cambia (fuente, tema) sin destruir la vista
                        val currentHash = content.hashCode()
                        if (view.tag != currentHash) {
                            view.tag      = currentHash
                            webViewRef.value = view
                            isPageReady   = false
                            view.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null)
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bg))
            }
        }

        // ── POPUP DE COLORES PARA HIGHLIGHTS ────────────────────────────────
        if (activeSelection != null) {
            val sel = activeSelection!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            activeSelection = null
                            webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                        }
                    }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = maxOf(16f, sel.x - 60f).dp, y = maxOf(64f, sel.y - 80f).dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("#FFF59D", "#A5D6A7", "#90CAF9", "#F48FB1").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable {
                                        scope.launch(Dispatchers.IO) {
                                            dao.insertAnnotation(
                                                com.example.moby.models.BookAnnotation(
                                                    publicationId = publicationId,
                                                    chapterPath   = internalPath,
                                                    cfiInfo       = sel.cfi,
                                                    selectedText  = sel.text,
                                                    colorHex      = hex
                                                )
                                            )
                                            withContext(Dispatchers.Main) {
                                                webViewRef.value?.evaluateJavascript("confirmHighlight('$hex');", null)
                                                activeSelection = null
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
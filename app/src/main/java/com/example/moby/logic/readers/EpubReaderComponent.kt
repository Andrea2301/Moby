package com.example.moby.logic.readers

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

import com.example.moby.ui.screens.ReaderTheme
import com.example.moby.logic.readers.epub.*

@Composable
fun EpubReaderComponent(
    publicationId: String,
    filePath: String,
    initialChapter: Int,
    initialVirtualPage: Int = 0,
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
                val cxml = zip.getInputStream(zip.getEntry("META-INF/container.xml")!!).bufferedReader().readText()
                val opfP = """<rootfile[^>]+full-path="([^"]+)"""".toRegex().find(cxml)?.groupValues?.get(1)!!
                opfDir = if (opfP.contains("/")) opfP.substringBeforeLast("/") + "/" else ""
                val opfX = zip.getInputStream(zip.getEntry(opfP)!!).bufferedReader().readText()
                val manifestMap = mutableMapOf<String, String>()
                """<item[^>]+id="([^"]+)"[^>]+href="([^"]+)"|<item[^>]+href="([^"]+)"[^>]+id="([^"]+)"""".toRegex()
                    .findAll(opfX).forEach { m ->
                        if (m.groupValues[1].isNotEmpty()) manifestMap[m.groupValues[1]] = m.groupValues[2]
                        if (m.groupValues[4].isNotEmpty()) manifestMap[m.groupValues[4]] = m.groupValues[3]
                    }
                val spineX = """<spine[^>]*>(.*?)</spine>""".toRegex(RegexOption.DOT_MATCHES_ALL).find(opfX)?.groupValues?.get(1)!!
                val chapterHs = """<itemref[^>]+idref="([^"]+)"""".toRegex()
                    .findAll(spineX).mapNotNull { manifestMap[it.groupValues[1]] }.toList()
                zip.close()
                withContext(Dispatchers.Main) { chapters = chapterHs; onTotalChaptersReady(chapterHs.size) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { fileLoadError = true } }
        }
    }

    DisposableEffect(Unit) { onDispose { EpubZipEngine.close() } }

    if (fileLoadError || chapters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (fileLoadError) Text("Error cargando el libro.") else CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialChapter.coerceIn(0, chapters.size - 1), pageCount = { chapters.size })
    val scope = rememberCoroutineScope()
    var virtualPageIndex by remember { mutableIntStateOf(initialVirtualPage) }
    val chapterPageCounts = remember { mutableStateMapOf<Int, Int>() }
    val totalBookPages = remember(chapterPageCounts.size) { chapters.indices.sumOf { chapterPageCounts[it] ?: 0 } }
    var landOnLastPage by remember { mutableStateOf(false) }
    var isFirstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage, virtualPageIndex, totalBookPages) {
        delay(600)
        val globalPage = (0 until pagerState.currentPage).sumOf { chapterPageCounts[it] ?: 0 } + (virtualPageIndex + 1)
        onVirtualPageChanged(globalPage, totalBookPages)
    }

    LaunchedEffect(pagerState.currentPage) {
        if (isFirstLoad) {
            // Primera carga: conserva initialVirtualPage para restaurar posición guardada
            isFirstLoad = false
        } else {
            if (!landOnLastPage) virtualPageIndex = 0
        }
        onChapterChanged(pagerState.currentPage)
    }

    LaunchedEffect(initialChapter) {
        val target = initialChapter.coerceIn(0, chapters.size - 1)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    // Guarda posición codificada: (capítulo+1)*10000 + paginaVirtual
    // Retrocompatible — valores guardados anteriores son < 10000
    LaunchedEffect(pagerState.currentPage, virtualPageIndex) {
        delay(500)
        val encoded = (pagerState.currentPage + 1) * 10000 + virtualPageIndex
        dao.updatePublicationPosition(publicationId, encoded)
    }

    val bg = theme.toColor()
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().background(bg),
            beyondViewportPageCount = 0
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                val abs = offset.absoluteValue.coerceIn(0f, 1f)
                scaleX = lerp(0.88f, 1f, 1f - abs); scaleY = lerp(0.88f, 1f, 1f - abs); alpha = lerp(0.5f, 1f, 1f - abs)
            }) {
                EpubChapterRender(
                    publicationId = publicationId, dao = dao, filePath = filePath,
                    internalPath = opfDir + android.net.Uri.decode(chapters[page]),
                    theme = theme, fontSize = fontSize, fontFamily = fontFamily, lineSpacing = lineSpacing,
                    isVerticalMode = isVerticalMode,
                    // -1 indica "ir a la última página" de forma segura controlada por JS
                    virtualPageIndex = if (page == pagerState.currentPage) {
                        if (landOnLastPage) -1 else virtualPageIndex
                    } else 0,
                    onChapterBoundary = { reachedEnd ->
                        if (reachedEnd) {
                            if (pagerState.currentPage < chapters.size - 1) {
                                landOnLastPage = false
                                virtualPageIndex = 0
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        } else {
                            if (pagerState.currentPage > 0) {
                                landOnLastPage = true
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        }
                    },
                    onVirtualPageIndexChanged = { idx -> 
                        if (page == pagerState.currentPage) {
                            virtualPageIndex = idx
                            if (landOnLastPage) landOnLastPage = false
                        }
                    },
                    onCenterTap = onCenterTap,
                    onVirtualPageCountReady = { count ->
                        chapterPageCounts[page] = count
                    }
                )
            }
        }
    }
}

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
    onChapterBoundary: (Boolean) -> Unit,
    onVirtualPageIndexChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onVirtualPageCountReady: (Int) -> Unit
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var rawBody by remember { mutableStateOf<String?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(internalPath) {
        isPageReady = false
        rawBody = null
        withContext(Dispatchers.IO) {
            try {
                val zip = ZipFile(File(filePath))
                val entry = zip.getEntry(internalPath)
                if (entry != null) {
                    val raw = zip.getInputStream(entry).bufferedReader().readText()
                    zip.close()
                    rawBody = Regex("(?si)<body[^>]*>(.*?)</body>").find(raw)?.groupValues?.get(1) ?: raw
                } else zip.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(theme, fontSize, fontFamily, lineSpacing, isVerticalMode) {
        val view = webViewRef.value ?: return@LaunchedEffect
        if (!isPageReady) return@LaunchedEffect
        val bgHex = theme.toBgHex(); val textHex = theme.toTextHex(); val fontStack = if (fontFamily == "Sans") "sans-serif" else "Georgia, serif"
        view.evaluateJavascript("""
            (function() {
                var r = document.documentElement;
                r.style.setProperty('--moby-bg', '$bgHex');
                r.style.setProperty('--moby-text', '$textHex');
                r.style.setProperty('--moby-font', '$fontStack');
                r.style.setProperty('--moby-size', '${fontSize.toInt()}%');
                r.style.setProperty('--moby-spacing', '$lineSpacing');
                setTimeout(function() { mobyMeasure(); mobySync(); }, 150);
            })();
        """.trimIndent(), null)
    }

    val bg = theme.toColor()
    Box(modifier = Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        if (rawBody != null) {
            val chapterDir = internalPath.substringBeforeLast("/", "")
            val baseUrl = if (chapterDir.isNotEmpty()) "moby-epub://book/$chapterDir/" else "moby-epub://book/"
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowContentAccess = true; allowFileAccess = false; textZoom = 100 }
                        addJavascriptInterface(object : Any() {
                            @android.webkit.JavascriptInterface fun onPageCountReady(count: Int) { scope.launch(Dispatchers.Main) { onVirtualPageCountReady(count) } }
                            @android.webkit.JavascriptInterface fun onChapterBoundary(reachedEnd: Boolean) { scope.launch(Dispatchers.Main) { onChapterBoundary(reachedEnd) } }
                            @android.webkit.JavascriptInterface fun onTapCenter() { scope.launch(Dispatchers.Main) { onCenterTap() } }
                            @android.webkit.JavascriptInterface fun onVirtualPageIndexChanged(idx: Int) { scope.launch(Dispatchers.Main) { onVirtualPageIndexChanged(idx) } }
                        }, "mobyBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageReady = true
                                view?.evaluateJavascript("mobyInit($virtualPageIndex);", null)
                            }
                            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                val uri = request?.url ?: return null
                                if (uri.scheme != "moby-epub") return null
                                val rawPath = uri.path ?: return null
                                val pathNoBook = if (rawPath.startsWith("/book/")) rawPath.substring(6) else rawPath.removePrefix("/")
                                val decoded = android.net.Uri.decode(pathNoBook)
                                val bytes = EpubZipEngine.readEntry(filePath, decoded) ?: EpubZipEngine.readEntry(filePath, "${internalPath.substringBeforeLast("/", "")}/$decoded") ?: return null
                                return android.webkit.WebResourceResponse(EpubZipEngine.getMimeType(decoded), "UTF-8", bytes.inputStream())
                            }
                        }
                        webViewRef.value = this
                    }
                },
                update = { view ->
                    if (view.tag != internalPath) {
                        view.tag = internalPath
                        isPageReady = false
                        val html = EpubHtmlContent.build(rawBody!!, theme, fontSize, fontFamily, lineSpacing, isVerticalMode, virtualPageIndex)
                        view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                    }
                }
            )
        } else {
            CircularProgressIndicator(color = theme.toTextHex().let { android.graphics.Color.parseColor(it) }.let { androidx.compose.ui.graphics.Color(it) })
        }
    }
}
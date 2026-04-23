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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import com.example.moby.models.BookAnnotation
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
    val totalBookPages by remember { derivedStateOf { chapters.indices.sumOf { chapterPageCounts[it] ?: 0 } } }
    var previousChapter by remember { mutableIntStateOf(initialChapter) }
    var isFirstLoad by remember { mutableStateOf(true) }
    var isNavigating by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, virtualPageIndex, totalBookPages) {
        delay(300)
        if (virtualPageIndex >= 0) {
            val globalPage = (0 until pagerState.currentPage).sumOf { chapterPageCounts[it] ?: 0 } + (virtualPageIndex + 1)
            onVirtualPageChanged(globalPage, totalBookPages)
        }
    }

    LaunchedEffect(initialChapter) {
        val target = initialChapter.coerceIn(0, chapters.size - 1)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    LaunchedEffect(pagerState.currentPage, virtualPageIndex) {
        if (!isFirstLoad && virtualPageIndex >= 0) {
            val encoded = (pagerState.currentPage + 1) * 10000 + virtualPageIndex
            onChapterChanged(encoded)
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        isFirstLoad = false
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isFirstLoad) {
            if (pagerState.currentPage < previousChapter) {
                virtualPageIndex = -1
            } else if (pagerState.currentPage > previousChapter) {
                virtualPageIndex = 0
            }
        }
        previousChapter = pagerState.currentPage
    }

    val bg = theme.toColor()
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            EpubChapterRender(
                publicationId = publicationId,
                dao = dao,
                filePath = filePath,
                internalPath = opfDir + android.net.Uri.decode(chapters[page]),
                theme = theme,
                fontSize = fontSize,
                fontFamily = fontFamily,
                lineSpacing = lineSpacing,
                isVerticalMode = isVerticalMode,
                virtualPageIndex = if (page == pagerState.currentPage) {
                    virtualPageIndex
                } else if (page < pagerState.currentPage) {
                    -1
                } else {
                    0
                },
                onVirtualPageCountReady = { count ->
                    chapterPageCounts[page] = count
                },
                onVirtualPageIndexChanged = { idx ->
                    if (page == pagerState.currentPage) {
                        virtualPageIndex = idx
                    }
                },
                onChapterBoundary = { reachedEnd ->
                    if (!isNavigating) {
                        if (reachedEnd && pagerState.currentPage < chapters.size - 1) {
                            scope.launch {
                                try {
                                    isNavigating = true
                                    virtualPageIndex = 0
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    delay(400)
                                } finally {
                                    isNavigating = false
                                }
                            }
                        } else if (!reachedEnd && pagerState.currentPage > 0) {
                            scope.launch {
                                try {
                                    isNavigating = true
                                    virtualPageIndex = -1
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    delay(400)
                                } finally {
                                    isNavigating = false
                                }
                            }
                        }
                    }
                },
                onCenterTap = onCenterTap,
                chapterTotalPages = chapterPageCounts[page] ?: 1
            )
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
    onVirtualPageCountReady: (Int) -> Unit,
    chapterTotalPages: Int
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var rawBody by remember { mutableStateOf<String?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // SELECTION STATE
    var showSelectionPopup by remember { mutableStateOf(false) }
    var selectionText by remember { mutableStateOf("") }
    var selectionCfi by remember { mutableStateOf("") }
    var selectionX by remember { mutableFloatStateOf(0f) }
    var selectionY by remember { mutableFloatStateOf(0f) }

    // Load annotations for this chapter to apply them on page load
    val annotations = remember(publicationId, internalPath) { 
        mutableStateListOf<BookAnnotation>() 
    }

    LaunchedEffect(publicationId, internalPath) {
        val list = dao.getAnnotationsForChapter(publicationId, internalPath)
        annotations.clear()
        annotations.addAll(list)
    }

    LaunchedEffect(internalPath) {
        isPageReady = false
        rawBody = null
        showSelectionPopup = false
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
                setTimeout(function() { mobyMeasure(); mobySync(); }, 80);
            })();
        """.trimIndent(), null)
    }

    // Apply saved highlights when page is ready
    LaunchedEffect(isPageReady, annotations.size) {
        if (isPageReady) {
            val view = webViewRef.value ?: return@LaunchedEffect
            annotations.forEach { ann ->
                view.evaluateJavascript("mobyApplyHighlight(`${ann.cfiInfo}`, '');", null)
            }
        }
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
                        settings.apply { 
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowContentAccess = true
                            allowFileAccess = false
                            textZoom = 100
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        isHapticFeedbackEnabled = false
                        
                        val bridge = EpubJavascriptBridge(
                            scope = scope,
                            onVirtualPageCountReady = onVirtualPageCountReady,
                            onVirtualPageIndexChanged = onVirtualPageIndexChanged,
                            onChapterBoundary = onChapterBoundary,
                            onTextSelectedRaw = { text, cfi, x, y, w, h ->
                                selectionText = text
                                selectionCfi = cfi
                                selectionX = x
                                selectionY = y
                                showSelectionPopup = true
                            },
                            onSelectionClearedRaw = {
                                showSelectionPopup = false
                            },
                            onCenterTap = onCenterTap
                        )
                        addJavascriptInterface(bridge, "mobyBridge")

                        setOnTouchListener { v, event ->
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            false
                        }

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
                    } else if (isPageReady) {
                        view.evaluateJavascript("""
                            if ($virtualPageIndex === -1) {
                                __mobyTarget = Math.max(0, __mobyCount - 1);
                                mobySync();
                                if (window.mobyBridge && window.mobyBridge.onVirtualPageIndexChanged) {
                                    window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget);
                                }
                            } else if (window.__mobyTarget !== $virtualPageIndex) {
                                __mobyTarget = $virtualPageIndex;
                                mobySync();
                            }
                        """.trimIndent(), null)
                    }
                }
            )
        } else {
            CircularProgressIndicator(color = theme.toTextHex().let { android.graphics.Color.parseColor(it) }.let { androidx.compose.ui.graphics.Color(it) })
        }

        // SELECTION POPUP
        if (showSelectionPopup) {
            EpubSelectionPopup(
                xdp = selectionX,
                ydp = selectionY,
                selectedText = selectionText,
                onHighlight = { color ->
                    val annotation = BookAnnotation(
                        publicationId = publicationId,
                        chapterPath = internalPath,
                        cfiInfo = selectionCfi,
                        selectedText = selectionText,
                        colorHex = color
                    )
                    scope.launch(Dispatchers.IO) {
                        dao.insertAnnotation(annotation)
                        withContext(Dispatchers.Main) {
                            annotations.add(annotation)
                            webViewRef.value?.evaluateJavascript("mobyApplyHighlight(`${selectionCfi}`, '');", null)
                            webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                            showSelectionPopup = false
                        }
                    }
                },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(selectionText))
                    webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                    showSelectionPopup = false
                },
                onDismiss = {
                    webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                    showSelectionPopup = false
                }
            )
        }
    }
}
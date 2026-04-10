package com.example.moby.logic.readers

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

import com.example.moby.ui.screens.ReaderTheme
import com.example.moby.logic.readers.epub.*

// =============================================================================
// EPUB READER COMPONENT
// =============================================================================
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

    DisposableEffect(Unit) { onDispose { EpubZipEngine.close() } }

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

    val virtualPageCount = chapterPageCounts[pagerState.currentPage] ?: 1
    val isChapterReady   = chapterPageCounts.containsKey(pagerState.currentPage)
    val totalBookPages   = remember(chapterPageCounts.size) {
        chapters.indices.sumOf { chapterPageCounts[it] ?: 0 }
    }

    var isMainChapterReady by remember { mutableStateOf(false) }
    var scanPointer        by remember { mutableIntStateOf(-1) }

    val scanSequence = remember(chapters.size, pagerState.currentPage) {
        if (chapters.isEmpty()) return@remember emptyList<Int>()
        val cur = pagerState.currentPage
        val result = mutableListOf(cur)
        var i = 1
        while (result.size < chapters.size) {
            if (cur + i < chapters.size) result.add(cur + i)
            if (cur - i >= 0)            result.add(cur - i)
            i++
        }
        result
    }

    LaunchedEffect(chapters.size, fontSize, fontFamily, lineSpacing, isVerticalMode) {
        if (chapters.isEmpty()) return@LaunchedEffect
        isMainChapterReady = false
        virtualPageIndex   = 0
        delay(800)
        chapterPageCounts.clear()
        scanPointer = 0
    }

    LaunchedEffect(isChapterReady) {
        if (isChapterReady) isMainChapterReady = true
    }

    val currentScanIdx = if (
        scanSequence.isNotEmpty() && scanPointer in scanSequence.indices
    ) scanSequence[scanPointer] else -1

    // Guardar posición con debounce
    LaunchedEffect(pagerState.currentPage, virtualPageIndex, totalBookPages) {
        delay(600)
        val globalPage = (0 until pagerState.currentPage)
            .sumOf { chapterPageCounts[it] ?: 0 } + (virtualPageIndex + 1)
        onVirtualPageChanged(globalPage, totalBookPages)
        if (totalBookPages > 0) dao.updatePublicationPosition(publicationId, globalPage)
    }

    LaunchedEffect(totalBookPages, isChapterReady) {
        if (totalBookPages > 0 && chapterPageCounts.size == chapters.size) {
            dao.updateTotalPages(publicationId, totalBookPages)
        }
    }

    var previousChapter by remember { mutableIntStateOf(pagerState.currentPage) }
    var landOnLastPage  by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val goingBack   = pagerState.currentPage < previousChapter
        previousChapter = pagerState.currentPage
        landOnLastPage  = goingBack
        virtualPageIndex = 0
        onChapterChanged(pagerState.currentPage)
    }

    LaunchedEffect(isChapterReady, virtualPageCount) {
        if (isChapterReady && landOnLastPage) {
            virtualPageIndex = maxOf(0, virtualPageCount - 1)
            landOnLastPage   = false
        }
    }

    LaunchedEffect(initialChapter) {
        val target = initialChapter.coerceIn(0, chapters.size - 1)
        if (pagerState.currentPage != target) {
            virtualPageIndex = 0
            landOnLastPage   = false
            pagerState.scrollToPage(target)
        }
    }

    // ── Navegación: fuente única de verdad ────────────────────────────────────
    // Estas lambdas son las ÚNICAS que modifican virtualPageIndex y el pager.
    // Tanto los botones del overlay como el bridge JS las llaman.
    // Al vivir aquí (en EpubReaderComponent) siempre leen el estado más reciente
    // — no hay riesgo de closures stale como ocurre cuando se pasan a composables hijos.
    val goLeft: () -> Unit = {
        if (!isVerticalMode) {
            if (virtualPageIndex > 0) {
                virtualPageIndex--
            } else if (pagerState.currentPage > 0) {
                landOnLastPage = true
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        } else {
            if (pagerState.currentPage > 0)
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    val goRight: () -> Unit = {
        if (!isVerticalMode) {
            val maxPage = chapterPageCounts[pagerState.currentPage] ?: 1
            if (virtualPageIndex < maxPage - 1) {
                virtualPageIndex++
            } else if (pagerState.currentPage < chapters.size - 1) {
                virtualPageIndex = 0
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        } else {
            if (pagerState.currentPage < chapters.size - 1)
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    val bg = theme.toColor()

    Box(modifier = Modifier.fillMaxSize()) {
        val pagerModifier = Modifier.fillMaxSize().background(bg)

        if (!isVerticalMode) {
            HorizontalPager(
                state = pagerState,
                // userScrollEnabled = false: la navegación es 100% por taps.
                // Habilitar el swipe del pager compite con el scroll del WebView
                // y produce saltos accidentales de capítulo completo.
                // beyondViewportPageCount = 0: Reducimos drásticamente el consumo de RAM.
                // El renderizado del siguiente capítulo comenzará solo cuando navegues a él,
                // eliminando el "lag" durante la lectura del capítulo actual.
                beyondViewportPageCount = 0
            ) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                    val abs = offset.absoluteValue.coerceIn(0f, 1f)
                    scaleX = lerp(0.88f, 1f, 1f - abs)
                    scaleY = lerp(0.88f, 1f, 1f - abs)
                    alpha  = lerp(0.5f,  1f, 1f - abs)
                }) {
                    EpubChapterRender(
                        publicationId    = publicationId, dao = dao,
                        filePath         = filePath,
                        internalPath     = opfDir + android.net.Uri.decode(chapters[page]),
                        theme            = theme, fontSize = fontSize,
                        fontFamily       = fontFamily, lineSpacing = lineSpacing,
                        isVerticalMode   = false,
                        virtualPageIndex = if (page == pagerState.currentPage) virtualPageIndex else 0,
                        onLeftTap        = goLeft, onRightTap = goRight, onCenterTap = onCenterTap,
                        onVirtualPageCountReady = { count -> chapterPageCounts[page] = count }
                    )
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                userScrollEnabled = true,
                modifier = pagerModifier
            ) { page ->
                EpubChapterRender(
                    publicationId    = publicationId, dao = dao,
                    filePath         = filePath,
                    internalPath     = opfDir + android.net.Uri.decode(chapters[page]),
                    theme            = theme, fontSize = fontSize,
                    fontFamily       = fontFamily, lineSpacing = lineSpacing,
                    isVerticalMode   = true, virtualPageIndex = 0,
                    onLeftTap        = goLeft, onRightTap = goRight, onCenterTap = onCenterTap,
                    onVirtualPageCountReady = { chapterPageCounts[page] = 1 }
                )
            }
        }

        // ── OVERLAY DE NAVEGACIÓN POR MÁRGENES ───────────────────────────────
        // Dos franjas laterales transparentes de 52dp.
        // Reciben taps directamente en Compose — sin pasar por el WebView,
        // sin bridge JS, sin latencia de roundtrip JS→Kotlin.
        // Son más fiables que el bridge porque:
        //   1. Compose las procesa en el mismo frame
        //   2. No dependen de que el WebView esté listo
        //   3. No se ven afectadas por recargas del WebView
        if (!isVerticalMode) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zona Izquierda: 80dp (Optimizado para pantallas curvas)
                // Activación en el instante del contacto (Down) para latencia cero.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    goLeft()
                                }
                            }
                        }
                )
                Spacer(modifier = Modifier.weight(1f))
                // Zona Derecha: 80dp (Optimizado para pantallas curvas)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    goRight()
                                }
                            }
                        }
                )
            }
        }

        // ── SCANNER INVISIBLE ─────────────────────────────────────────────────
        if (currentScanIdx in chapters.indices && isMainChapterReady) {
            EpubScannerComponent(
                publicationId  = publicationId,
                filePath       = filePath,
                internalPath   = opfDir + android.net.Uri.decode(chapters[currentScanIdx]),
                theme          = theme, fontSize = fontSize,
                fontFamily     = fontFamily, lineSpacing = lineSpacing,
                isVerticalMode = isVerticalMode,
                onPageCountReady = { count ->
                    chapterPageCounts[currentScanIdx] = count
                    scanPointer++
                }
            )
        }
    }
}

// =============================================================================
// EPUB CHAPTER RENDER
// =============================================================================
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

    // Scroll a página virtual
    LaunchedEffect(virtualPageIndex, isPageReady) {
        if (!isVerticalMode && isPageReady) {
            // Latencia Cero: Ejecución inmediata sin delay artificial.
            webViewRef.value?.evaluateJavascript(
                """(function(){
                    var w = window.__mobyW || document.documentElement.clientWidth || window.innerWidth;
                    if (w > 0) window.scrollTo(${virtualPageIndex} * w, 0);
                })();""", null
            )
        }
    }

    // Cargar HTML — sin delay(250), con clave estable
    LaunchedEffect(internalPath, isVerticalMode, fontSize, fontFamily, lineSpacing, theme) {
        isPageReady = false
        htmlContent = null
        withContext(Dispatchers.IO) {
            try {
                val z = ZipFile(File(filePath))
                val e = z.getEntry(internalPath)
                if (e != null) {
                    val raw = z.getInputStream(e).bufferedReader().readText(); z.close()
                    val bodyContent = Regex("(?si)<body[^>]*>(.*?)</body>")
                        .find(raw)?.groupValues?.get(1) ?: raw
                    val html = EpubHtmlContent.build(
                        bodyContent      = bodyContent,
                        theme            = theme,
                        fontSize         = fontSize,
                        fontFamily       = fontFamily,
                        lineSpacing      = lineSpacing,
                        isVerticalMode   = isVerticalMode,
                        virtualPageIndex = virtualPageIndex
                    )
                    withContext(Dispatchers.Main) { htmlContent = html }
                } else z.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val bg = theme.toColor()

    Box(modifier = Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        // Animar por internalPath, no por htmlContent.
        // htmlContent es un String nuevo en cada carga aunque el capítulo sea el mismo —
        // animarlo causaba transiciones innecesarias y creaba WebViews duplicados.
        AnimatedContent(
            targetState = internalPath,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            contentAlignment = Alignment.Center,
            label = "EpubFade"
        ) { animatedPath ->
            val content = htmlContent

            if (content != null && animatedPath == internalPath) {
                val chapterDir = animatedPath.substringBeforeLast("/", "")
                val baseUrl = if (chapterDir.isNotEmpty()) "moby-epub://book/$chapterDir/"
                else "moby-epub://book/"

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
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
                                cacheMode            = android.webkit.WebSettings.LOAD_NO_CACHE
                                mixedContentMode     = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            val bridge = EpubJavascriptBridge(
                                scope = scope,
                                onVirtualPageCountReady = onVirtualPageCountReady,
                                onTextSelectedRaw = { text, cfi, left, top ->
                                    activeSelection = SelectionInfo(text, cfi, left, top)
                                },
                                onSelectionClearedRaw = { activeSelection = null },
                                onLeftTap   = onLeftTap,
                                onRightTap  = onRightTap,
                                onCenterTap = onCenterTap
                            )
                            addJavascriptInterface(bridge, "mobyBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript("mobyInit($virtualPageIndex, 0);", null)
                                    isPageReady = true

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

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val uri = request?.url ?: return null
                                    if (uri.scheme != "moby-epub") return null
                                    val decoded = android.net.Uri.decode(
                                        uri.path?.removePrefix("/") ?: return null
                                    )
                                    val bytes = EpubZipEngine.readEntry(filePath, decoded) ?: run {
                                        val cDir = internalPath.substringBeforeLast("/", "")
                                        EpubZipEngine.readEntry(
                                            filePath,
                                            if (cDir.isNotEmpty()) "$cDir/$decoded" else decoded
                                        )
                                    } ?: return null
                                    return android.webkit.WebResourceResponse(
                                        EpubZipEngine.getMimeType(decoded), "UTF-8", bytes.inputStream()
                                    )
                                }
                            }
                        }
                    },
                    update = { view ->
                        // Usamos un Hash del contenido para forzar recarga si cambian fuentes/temas
                        val contentKey = animatedPath + (content?.hashCode() ?: 0)
                        if (view.tag != contentKey) {
                            view.tag = contentKey
                            webViewRef.value = view
                            isPageReady = false
                            view.loadDataWithBaseURL(baseUrl, content!!, "text/html", "utf-8", null)
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bg))
            }
        }

        if (activeSelection != null) {
            EpubSelectionPopup(
                xdp = activeSelection!!.x,
                ydp = activeSelection!!.y,
                onColorSelected = { hex ->
                    val sel = activeSelection!!
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
                },
                onDismiss = {
                    activeSelection = null
                    webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                }
            )
        }
    }
}

// =============================================================================
// EPUB SCANNER COMPONENT
// =============================================================================
@Composable
fun EpubScannerComponent(
    publicationId: String,
    filePath: String,
    internalPath: String,
    theme: ReaderTheme,
    fontSize: Float,
    fontFamily: String,
    lineSpacing: Float,
    isVerticalMode: Boolean,
    onPageCountReady: (Int) -> Unit
) {
    var rawHtml by remember(internalPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(internalPath, fontSize, fontFamily, lineSpacing, theme) {
        rawHtml = null
        withContext(Dispatchers.IO) {
            try {
                val z = ZipFile(File(filePath))
                val e = z.getEntry(internalPath)
                if (e != null) {
                    val raw = z.getInputStream(e).bufferedReader().readText(); z.close()
                    val body = Regex("(?si)<body[^>]*>(.*?)</body>")
                        .find(raw)?.groupValues?.get(1) ?: raw
                    val html = EpubHtmlContent.build(
                        body, theme, fontSize, fontFamily, lineSpacing, isVerticalMode, 0
                    )
                    withContext(Dispatchers.Main) { rawHtml = html }
                } else z.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    if (rawHtml != null) {
        val chapterDir = internalPath.substringBeforeLast("/", "")
        val baseUrl    = if (chapterDir.isNotEmpty()) "moby-epub://book/$chapterDir/"
        else "moby-epub://book/"

        // alpha = 0.01f en lugar de 0f: con alpha exactamente 0 algunos sistemas
        // omiten el layout del composable y el WebView no puede medir scrollWidth.
        Box(modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0.01f }) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled    = true
                            textZoom             = 100
                            useWideViewPort      = false
                            loadWithOverviewMode = false
                            cacheMode            = android.webkit.WebSettings.LOAD_NO_CACHE
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("mobyInit(0, 0);", null)
                            }
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val uri = request?.url ?: return null
                                if (uri.scheme != "moby-epub") return null
                                val decoded = android.net.Uri.decode(
                                    uri.path?.removePrefix("/") ?: return null
                                )
                                val bytes = EpubZipEngine.readEntry(filePath, decoded) ?: run {
                                    val cDir = internalPath.substringBeforeLast("/", "")
                                    EpubZipEngine.readEntry(
                                        filePath,
                                        if (cDir.isNotEmpty()) "$cDir/$decoded" else decoded
                                    )
                                } ?: return null
                                return android.webkit.WebResourceResponse(
                                    EpubZipEngine.getMimeType(decoded), "UTF-8", bytes.inputStream()
                                )
                            }
                        }
                        addJavascriptInterface(object : Any() {
                            @android.webkit.JavascriptInterface
                            fun onPageCountReady(count: Int) {
                                post { onPageCountReady(if (isVerticalMode) 1 else maxOf(1, count)) }
                            }
                        }, "mobyBridge")
                    }
                },
                update = { view ->
                    val htmlKey = internalPath + fontSize + lineSpacing
                    if (view.tag != htmlKey) {
                        view.tag = htmlKey
                        view.loadDataWithBaseURL(baseUrl, rawHtml!!, "text/html", "utf-8", null)
                    }
                }
            )
        }
    }
}
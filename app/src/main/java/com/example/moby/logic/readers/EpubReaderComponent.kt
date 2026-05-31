package com.example.moby.logic.readers

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
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
    onChaptersLoaded: (List<String>) -> Unit = {},
    fontSize: Float = 100f,
    fontFamily: String = "Serif",
    lineSpacing: Float = 1.65f,
    isVerticalMode: Boolean,
    theme: ReaderTheme,
    onCenterTap: () -> Unit,
    onToggleBookmarkRequested: ((() -> Unit) -> Unit)? = null,
    activeSearchQuery: String? = null
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
                withContext(Dispatchers.Main) { 
                    chapters = chapterHs
                    onTotalChaptersReady(chapterHs.size)
                    onChaptersLoaded(chapterHs)
                }
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
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
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
                onVirtualPageCountReady = { count: Int ->
                    chapterPageCounts[page] = count
                },
                onVirtualPageIndexChanged = { idx: Int ->
                    if (page == pagerState.currentPage) {
                        virtualPageIndex = idx
                    }
                },
                onChapterBoundary = { reachedEnd: Boolean ->
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
                chapterTotalPages = chapterPageCounts[page] ?: 1,
                onToggleBookmarkRequested = if (page == pagerState.currentPage) onToggleBookmarkRequested else null,
                activeSearchQuery = if (page == pagerState.currentPage) activeSearchQuery else null
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
    chapterTotalPages: Int,
    onToggleBookmarkRequested: ((() -> Unit) -> Unit)? = null,
    activeSearchQuery: String? = null
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
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
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

    val htmlContent = remember(rawBody, theme, fontSize, fontFamily, lineSpacing, isVerticalMode) {
        val body = rawBody ?: ""
        EpubHtmlContent.build(body, theme, fontSize, fontFamily, lineSpacing, isVerticalMode, virtualPageIndex)
    }

    val chapterDir = internalPath.substringBeforeLast("/", "")
    val baseUrl = if (chapterDir.isNotEmpty()) "moby-epub://book/$chapterDir/" else "moby-epub://book/"

    LaunchedEffect(htmlContent) {
        val view = webViewRef.value ?: return@LaunchedEffect
        isPageReady = false
        view.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
    }

    // Register the toggle action ONLY when this chapter is active
    LaunchedEffect(onToggleBookmarkRequested) {
        if (onToggleBookmarkRequested != null) {
            onToggleBookmarkRequested.invoke {
                // Toast de depuración para saber que la orden llegó a Kotlin
                android.widget.Toast.makeText(context, "Motor: Procesando marcador...", android.widget.Toast.LENGTH_SHORT).show()
                webViewRef.value?.evaluateJavascript("if(window.mobyRequestToggleBookmark) window.mobyRequestToggleBookmark();", null)
            }
        }
    }

    // Apply saved highlights and bookmarks when page is ready
    LaunchedEffect(isPageReady, annotations.size) {
        if (isPageReady) {
            val view = webViewRef.value ?: return@LaunchedEffect
            
            // 1. Send Bookmarks
            val bookmarksCfis = annotations.filter { it.selectedText.isEmpty() }.map { it.cfiInfo }
            val bookmarksJson = "[" + bookmarksCfis.joinToString(",") { "'$it'" } + "]"
            view.evaluateJavascript("mobyUpdateBookmarks(\"$bookmarksJson\");", null)

            // 2. Apply Highlights
            annotations.filter { it.selectedText.isNotEmpty() }.forEach { ann ->
                view.evaluateJavascript("mobyApplyHighlight(`${ann.cfiInfo}`, '${ann.colorHex}');", null)
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
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
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
                            context = context,
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
                            onCenterTap = onCenterTap,
                            onBookmarkToggled = { cfi ->
                                scope.launch(Dispatchers.IO) {
                                    // Comprobar si ya existe un marcador cerca de este CFI
                                    val existing = annotations.find { it.cfiInfo == cfi && it.selectedText.isEmpty() }
                                    if (existing != null) {
                                        dao.deleteAnnotationById(existing.id)
                                        withContext(Dispatchers.Main) { annotations.remove(existing) }
                                    } else {
                                        val newBookmark = BookAnnotation(
                                            publicationId = publicationId,
                                            chapterPath = internalPath,
                                            cfiInfo = cfi,
                                            selectedText = "",
                                            colorHex = "#FF5252"
                                        )
                                        dao.insertAnnotation(newBookmark)
                                        withContext(Dispatchers.Main) { 
                                            annotations.add(newBookmark)
                                            android.widget.Toast.makeText(context, "Marcador guardado", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        addJavascriptInterface(bridge, "mobyBridge")

                        setOnTouchListener { v, event ->
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            false
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageReady = true
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
                                if (window.mobyBridge) {
                                    window.mobyBridge.onVirtualPageIndexChanged(__mobyTarget.toString());
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
                            webViewRef.value?.evaluateJavascript("mobyApplyHighlight(`${selectionCfi}`, '$color');", null)
                            webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                            showSelectionPopup = false
                        }
                    }
                },
                onAddNote = {
                    showNoteDialog = true
                    showSelectionPopup = false
                    noteText = ""
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

        if (showNoteDialog) {
            val noteColors = listOf(
                Color(0xFFFFB7B2), // Peach Pink
                Color(0xFFFFF275), // Soft Yellow
                Color(0xFFB5EAD7), // Mint Green
                Color(0xFFC7CEEA), // Pastel Blue
                Color(0xFFD8B4F8), // Lavender
                Color(0xFFFFC6FF)  // Light Coral
            )
            var selectedColor by remember { mutableStateOf(noteColors[1]) } // Default yellow
            
            androidx.compose.ui.window.Dialog(onDismissRequest = { showNoteDialog = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. THE STICKY NOTE CARD (Tarjeta Adhesiva)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black.copy(alpha = 0.15f),
                                spotColor = Color.Black.copy(alpha = 0.15f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = selectedColor,
                        contentColor = Color(0xFF2C3E50) // Deep Slate text for readability
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            // Header inside sticky note: small icon + placeholder style
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = null,
                                    tint = Color(0xFF2C3E50).copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Añadir texto a esta nota",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50).copy(alpha = 0.5f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Large borderless text field inside the sticky note
                            TextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = { 
                                    Text(
                                        "Escribe tus pensamientos aquí...", 
                                        color = Color(0xFF2C3E50).copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyLarge
                                    ) 
                                },
                                modifier = Modifier.fillMaxSize(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF2C3E50),
                                    unfocusedTextColor = Color(0xFF2C3E50)
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 2. CONTROL PANEL (Panel de control del color y texto)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = Color.Black.copy(alpha = 0.12f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Header: Color de la nota
                            Text(
                                text = "Color de la nota",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Color row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                noteColors.forEach { color ->
                                    val isSelected = selectedColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.08f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable { selectedColor = color },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Header: Texto citado
                            Text(
                                text = "Texto citado",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Citation Block
                            Surface(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "“$selectionText”",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Save and Cancel buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showNoteDialog = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancelar", fontWeight = FontWeight.SemiBold)
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = {
                                        val colorHex = String.format("#%06X", (0xFFFFFF and selectedColor.toArgb()))
                                        val annotation = BookAnnotation(
                                            publicationId = publicationId,
                                            chapterPath = internalPath,
                                            cfiInfo = selectionCfi,
                                            selectedText = selectionText,
                                            colorHex = colorHex,
                                            note = noteText
                                        )
                                        scope.launch(Dispatchers.IO) {
                                            dao.insertAnnotation(annotation)
                                            withContext(Dispatchers.Main) {
                                                annotations.add(annotation)
                                                webViewRef.value?.evaluateJavascript("mobyApplyHighlight(`${selectionCfi}`, '$colorHex');", null)
                                                webViewRef.value?.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                                                showNoteDialog = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Guardar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
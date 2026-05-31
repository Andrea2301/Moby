package com.example.moby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moby.data.db.MobyDatabase
import com.example.moby.models.Publication
import com.example.moby.models.PublicationFormat
import com.example.moby.logic.readers.PdfReaderComponent
import com.example.moby.logic.readers.CbzReaderComponent
import com.example.moby.logic.readers.EpubReaderComponent
import com.example.moby.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReaderTheme {
    ARRECIFE,
    CRETA,
    PAPIRUS,
    ABISAL,
    ONYX
}

@Composable
fun ReaderScreen(
    publicationId: String, 
    onBack: () -> Unit,
    isAbisal: Boolean,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    val dao = remember { MobyDatabase.getDatabase(context).publicationDao() }
    val scope = rememberCoroutineScope()
    
    var publication by remember { mutableStateOf<Publication?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var chapterPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var virtualPageIndex by remember { mutableIntStateOf(0) }
    var virtualPageCount by remember { mutableIntStateOf(1) }
    
    // --- PERSISTENT READER SETTINGS (Collected from DataStore) ---
    val savedTheme: String by preferencesManager.readerThemeFlow.collectAsState(initial = "ARRECIFE")
    val savedFontSize: Float by preferencesManager.fontSizeFlow.collectAsState(initial = 100f)
    val savedFontFamily: String by preferencesManager.fontFamilyFlow.collectAsState(initial = "Serif")
    val savedLineSpacing: Float by preferencesManager.lineSpacingFlow.collectAsState(initial = 1.6f)
    val savedBrightness: Float by preferencesManager.brightnessFlow.collectAsState(initial = 1.0f)

    var readerTheme: ReaderTheme by remember { mutableStateOf(ReaderTheme.valueOf(savedTheme)) }
    var fontSize: Float by remember { mutableFloatStateOf(savedFontSize) }
    var fontFamily: String by remember { mutableStateOf(savedFontFamily) }
    var lineSpacing: Float by remember { mutableFloatStateOf(savedLineSpacing) }
    var brightness: Float by remember { mutableFloatStateOf(savedBrightness) }
    var showSettings: Boolean by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var isSmartFitEnabled: Boolean by remember { mutableStateOf(false) }
    var isTextReflowEnabled: Boolean by remember { mutableStateOf(false) }
    var isVerticalMode: Boolean by remember { mutableStateOf(false) }
    var isRtlEnabled: Boolean by remember { mutableStateOf(false) }
    var isWebtoonMode: Boolean by remember { mutableStateOf(false) }
    
    var bookmarkAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Sync local state when DataStore loads (first time or from other places)
    LaunchedEffect(savedTheme) { readerTheme = ReaderTheme.valueOf(savedTheme) }
    LaunchedEffect(savedFontSize) { fontSize = savedFontSize }
    LaunchedEffect(savedFontFamily) { fontFamily = savedFontFamily }
    LaunchedEffect(savedLineSpacing) { lineSpacing = savedLineSpacing }
    LaunchedEffect(savedBrightness) { brightness = savedBrightness }

    LaunchedEffect(publicationId) {
        withContext(Dispatchers.IO) {
            val pub = dao.getPublicationById(publicationId)
            withContext(Dispatchers.Main) {
                publication = pub
                isLoading = false
                currentPage = pub?.currentPosition ?: 0
                isTextReflowEnabled = pub?.isTextReflowEnabled ?: false
                var isVerticalMode = pub?.isVerticalMode ?: false
                isSmartFitEnabled = pub?.isSmartFitEnabled ?: false
                isRtlEnabled = pub?.isRtlEnabled ?: false
                isWebtoonMode = pub?.isWebtoonMode ?: false
            }
        }
    }

    val bookmarks by dao.getAllAnnotationsForPublication(publicationId).collectAsState(initial = emptyList())
    val pageBookmarks = remember(bookmarks) { bookmarks.filter { it.selectedText.isEmpty() } }

    val isDark = readerTheme == ReaderTheme.ABISAL || readerTheme == ReaderTheme.ONYX
    val backgroundColor = when (readerTheme) {
        ReaderTheme.ARRECIFE -> Color(0xFFF8F9FA)
        ReaderTheme.CRETA -> Color(0xFFF4ECD8)
        ReaderTheme.PAPIRUS -> Color(0xFFD2D2D2)
        ReaderTheme.ABISAL -> Color(0xFF011627)
        ReaderTheme.ONYX -> Color.Black
    }

    if (isLoading || publication == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor), 
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = if (isDark) Color.White else Color.Gray)
            } else {
                Text("Error: Libro no encontrado.", color = if (isDark) Color.White else Color.Black)
            }
        }
        return
    }

    val pub = publication!!

    // Decodifica posición guardada: valores >= 10000 usan formato (cap+1)*10000 + páginaVirtual
    // Valores < 10000 son el formato anterior (solo índice de capítulo)
    val rawPos = pub.currentPosition
    val savedChapter = if (rawPos >= 10000) (rawPos / 10000) - 1 else rawPos
    val savedVirtualPage = if (rawPos >= 10000) rawPos % 10000 else 0

    //  DYNAMIC SYSTEM BARS COLOR: Match the reader theme
    // This removes the white bar issue by making sys bars transparent or matching bg
    val sideEffectScope = rememberCoroutineScope()
    LaunchedEffect(backgroundColor) {
        // Here we could use a SystemUiController if available, 
        // but since we are edge-to-edge, the root Box background covers it.
        // We just need to ensure the icons are visible (dark/light)
    }

    val progressUpdateHandler: (Int) -> Unit = { page ->
        currentPage = page
        scope.launch(Dispatchers.IO) {
            dao.updatePublicationPosition(pub.id, page)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // 1. READER CONTENT LAYER
        Box(modifier = Modifier.fillMaxSize()) {
            when (pub.format) {
                PublicationFormat.PDF -> {
                    PdfReaderComponent(
                        filePath = pub.filePath,
                        initialPage = currentPage,
                        onPageChanged = progressUpdateHandler,
                        onTotalPagesReady = { totalPages = it },
                        isVerticalMode = isVerticalMode,
                        theme = readerTheme,
                        isSmartFitEnabled = isSmartFitEnabled,
                        isTextReflowEnabled = isTextReflowEnabled,
                        isRtlEnabled = isRtlEnabled,
                        isWebtoonMode = isWebtoonMode,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        onCenterTap = {
                            if (showSettings || showChapterList) {
                                showSettings = false; showChapterList = false
                            } else showControls = !showControls
                        }
                    )
                }
                PublicationFormat.EPUB -> {
                    val epChapter = if (currentPage >= 10000) (currentPage / 10000) - 1 else currentPage
                    val epVirtualPage = if (currentPage >= 10000) currentPage % 10000 else 0
                    
                    EpubReaderComponent(
                        publicationId = pub.id,
                        filePath = pub.filePath,
                        initialChapter = epChapter,
                        initialVirtualPage = epVirtualPage,
                         onChapterChanged = progressUpdateHandler,
                        onVirtualPageChanged = { index, count -> virtualPageIndex = index; virtualPageCount = count },
                        onTotalChaptersReady = { totalPages = it },
                        onChaptersLoaded = { paths -> chapterPaths = paths },
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        lineSpacing = lineSpacing,
                        isVerticalMode = pub.isVerticalMode,
                        theme = readerTheme,
                        onCenterTap = {
                            showControls = !showControls
                            if (!showControls) {
                                showSettings = false
                                showChapterList = false
                            }
                        },
                        onToggleBookmarkRequested = { action -> bookmarkAction = action }
                    )
                }
                PublicationFormat.CBZ -> {
                    CbzReaderComponent(
                        filePath = pub.filePath,
                        initialPage = currentPage,
                        onPageChanged = progressUpdateHandler,
                        onTotalPagesReady = { totalPages = it },
                        isVerticalMode = pub.isVerticalMode,
                        onCenterTap = {
                            if (showSettings || showChapterList) {
                                showSettings = false; showChapterList = false
                            } else showControls = !showControls
                        }
                    )
                }
                else -> Text("Format not supported")
            }
        }


        
        // BRIGHTNESS DIMMER OVERLAY
        if (brightness < 1.0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1.0f - brightness)))
        }

        // OVERLAY BACKGROUND FOR SIDE MENU
        if (showChapterList) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showChapterList = false }
            )
        }

        // SIDE MENU (Chapter List)
        AnimatedVisibility(
            visible = showChapterList,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Índice", "Favoritos", "Notas")

                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)) }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { 
                                    Text(
                                        text = title, 
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelMedium
                                    ) 
                                }
                            )
                        }
                    }
                    
                    when (selectedTab) {
                        0 -> {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize().navigationBarsPadding()
                            ) {
                                items(totalPages) { index ->
                                    val isSelected = index == currentPage
                                    androidx.compose.material3.TextButton(
                                        onClick = { 
                                            progressUpdateHandler(index)
                                            showChapterList = false 
                                            showControls = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
                                        shape = androidx.compose.ui.graphics.RectangleShape
                                    ) {
                                        Text(
                                            text = if (pub.format == PublicationFormat.EPUB) "Capítulo ${index + 1}" else "Página ${index + 1}",
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                         1 -> {
                            // Filtramos para mostrar TODO lo que tenga texto (resaltados)
                            val insights = bookmarks.filter { it.selectedText.isNotEmpty() }
                            
                            if (insights.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Reading Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Resalta frases que te emocionen para verlas aquí.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(insights.size) { index ->
                                        val insight = insights[index]
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().clickable { 
                                                val chapterIdx = chapterPaths.indexOfFirst { it.contains(insight.chapterPath.substringAfterLast("/")) }
                                                if (chapterIdx != -1) {
                                                    // Navegamos al capítulo
                                                    progressUpdateHandler(chapterIdx)
                                                }
                                                showChapterList = false 
                                                showControls = false
                                            }
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(android.graphics.Color.parseColor(insight.colorHex))))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        insight.chapterPath.substringAfterLast("/").substringBeforeLast("."),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "“${insight.selectedText}”",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault()).format(java.util.Date(insight.createdAt)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                        // Filter notes that have a user note attached
                        val userNotes = bookmarks.filter { !it.note.isNullOrEmpty() }

                        if (userNotes.isEmpty()) {
                            // Empty state with illustration and description
                            Box(
                                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Tus Reflexiones",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Aquí aparecerán tus pensamientos sobre el libro.",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // List of notes with rich cards
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(userNotes.size) { index ->
                                    val note = userNotes[index]
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Navigate to the chapter of the note
                                                val chapterIdx = chapterPaths.indexOfFirst { it.contains(note.chapterPath.substringAfterLast("/")) }
                                                if (chapterIdx != -1) progressUpdateHandler(chapterIdx)
                                                showChapterList = false
                                                showControls = false
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Color indicator dot
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(android.graphics.Color.parseColor(note.colorHex)))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    note.chapterPath.substringAfterLast("/").substringBeforeLast("."),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                // Timestamp
                                                Text(
                                                    java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            // User note content
                                            Text(
                                                note.note ?: "",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            // Original selected text preview
                                            Surface(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "“${note.selectedText}”",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = Color.Gray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // TOP BAR (Controls)
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(color = Color.Black.copy(alpha = 0.8f), contentColor = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(pub.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (isVerticalMode || isWebtoonMode) "Vertical" else "Horizontal", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = { showChapterList = true; showControls = false }) {
                        Icon(Icons.Default.FormatListBulleted, "Chapters")
                    }
                    IconButton(onClick = { showSettings = true; showControls = false }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            }
        }

        // BOTTOM BAR (Page Slider & Progress)
        AnimatedVisibility(
            visible = showControls && !showSettings && !showChapterList,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isEpub = pub.format == PublicationFormat.EPUB
                        val decodedPage = if (isEpub && currentPage >= 10000) (currentPage / 10000) - 1 else currentPage
                        val displayLabel = if (isEpub) "Cap. ${decodedPage + 1}" else "Pág. ${decodedPage + 1}"
                        
                        val progressPercent = if (isEpub) {
                            if (virtualPageCount > 10) {
                                ((virtualPageIndex.toFloat() / virtualPageCount) * 100).toInt().coerceIn(0, 100)
                            } else {
                                if (totalPages > 0) ((decodedPage.toFloat() / totalPages) * 100).toInt().coerceIn(0, 100) else 0
                            }
                        } else {
                            if (totalPages > 1) ((currentPage.toFloat() / (totalPages - 1)) * 100).toInt().coerceIn(0, 100) else 0
                        }

                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (isEpub) "$totalPages Cap" else "$totalPages Pág",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Slider(
                        value = (if (pub.format == PublicationFormat.EPUB && currentPage >= 10000) (currentPage / 10000) - 1 else currentPage).toFloat(),
                        onValueChange = { 
                            // En EPUB, el slider navega por capítulos
                            progressUpdateHandler(it.toInt()) 
                        },
                        valueRange = 0f..maxOf(1f, (totalPages - 1).toFloat()),
                        steps = if (totalPages in 2..50) totalPages - 2 else 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        )
                    )
                }
            }
        }

        // SETTINGS HUD
        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ReaderSettingsBottomPanel(
                theme = readerTheme,
                onThemeChange = { 
                    readerTheme = it
                    scope.launch { preferencesManager.setReaderSettings(theme = it.name) }
                },
                fontSize = fontSize,
                onFontSizeChange = { 
                    fontSize = it
                    scope.launch { preferencesManager.setReaderSettings(fontSize = it) }
                },
                fontFamily = fontFamily,
                onFontFamilyChange = { 
                    fontFamily = it
                    scope.launch { preferencesManager.setReaderSettings(fontFamily = it) }
                },
                lineSpacing = lineSpacing,
                onLineSpacingChange = { 
                    lineSpacing = it
                    scope.launch { preferencesManager.setReaderSettings(lineSpacing = it) }
                },
                brightness = brightness,
                onBrightnessChange = { 
                    brightness = it
                    scope.launch { preferencesManager.setReaderSettings(brightness = it) }
                },
                isSmartFitEnabled = isSmartFitEnabled,
                onSmartFitChange = { enabled ->
                    isSmartFitEnabled = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateSmartFitEnabled(pub.id, enabled)
                    }
                },
                isTextReflowEnabled = isTextReflowEnabled,
                onTextReflowChange = { enabled ->
                    isTextReflowEnabled = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateTextReflowEnabled(pub.id, enabled)
                    }
                },
                isVerticalMode = isVerticalMode,
                onVerticalModeChange = { enabled ->
                    isVerticalMode = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateVerticalMode(pub.id, enabled)
                    }
                },
                isRtlEnabled = isRtlEnabled,
                onRtlChange = { enabled ->
                    isRtlEnabled = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateRtlEnabled(pub.id, enabled)
                    }
                },
                isWebtoonMode = isWebtoonMode,
                onWebtoonChange = { enabled ->
                    isWebtoonMode = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateWebtoonMode(pub.id, enabled)
                    }
                },
                isPdf = pub.format == PublicationFormat.PDF,
                onSearchClick = {
                    showSearchDialog = true
                    showSettings = false
                }
            )
        }

        SearchInBookDialog(
            show = showSearchDialog,
            onDismiss = { showSearchDialog = false },
            filePath = pub.filePath,
            chapterPaths = chapterPaths,
            onResultClick = { chapterIndex ->
                progressUpdateHandler(chapterIndex)
                showSearchDialog = false
            }
        )
    }
}

@Composable
fun ReaderSettingsBottomPanel(
    theme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    fontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    lineSpacing: Float,
    onLineSpacingChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isSmartFitEnabled: Boolean = false,
    onSmartFitChange: (Boolean) -> Unit = {},
    isTextReflowEnabled: Boolean = false,
    onTextReflowChange: (Boolean) -> Unit = {},
    isVerticalMode: Boolean = false,
    onVerticalModeChange: (Boolean) -> Unit = {},
    isRtlEnabled: Boolean = false,
    onRtlChange: (Boolean) -> Unit = {},
    isWebtoonMode: Boolean = false,
    onWebtoonChange: (Boolean) -> Unit = {},
    isPdf: Boolean = false,
    onSearchClick: () -> Unit = {}
) {
    var currentView by remember { mutableStateOf("main") }
    val panelBg = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        color = panelBg.copy(alpha = 0.98f),
        contentColor = contentColor,
        tonalElevation = 12.dp
    ) {
        AnimatedContent(
            targetState = currentView,
            transitionSpec = {
                if (targetState == "advanced") {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "SettingsTransition"
        ) { view ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                when (view) {
                    "main" -> MainSettingsView(
                        theme = theme,
                        onThemeChange = onThemeChange,
                        brightness = brightness,
                        onBrightnessChange = onBrightnessChange,
                        fontSize = fontSize,
                        onFontSizeChange = onFontSizeChange,
                        isVerticalMode = isVerticalMode,
                        onVerticalModeChange = onVerticalModeChange,
                        isRtlEnabled = isRtlEnabled,
                        onRtlChange = onRtlChange,
                        isWebtoonMode = isWebtoonMode,
                        onWebtoonChange = onWebtoonChange,
                        isPdf = isPdf,
                        onNavigateToAdvanced = { currentView = "advanced" },
                        onSearchClick = onSearchClick
                    )
                    "advanced" -> AdvancedSettingsView(
                        fontFamily = fontFamily,
                        onFontFamilyChange = onFontFamilyChange,
                        lineSpacing = lineSpacing,
                        onLineSpacingChange = onLineSpacingChange,
                        isSmartFitEnabled = isSmartFitEnabled,
                        onSmartFitChange = onSmartFitChange,
                        isTextReflowEnabled = isTextReflowEnabled,
                        onTextReflowChange = onTextReflowChange,
                        onBack = { currentView = "main" },
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MainSettingsView(
    theme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    isVerticalMode: Boolean,
    onVerticalModeChange: (Boolean) -> Unit,
    isRtlEnabled: Boolean,
    onRtlChange: (Boolean) -> Unit,
    isWebtoonMode: Boolean,
    onWebtoonChange: (Boolean) -> Unit,
    isPdf: Boolean,
    onNavigateToAdvanced: () -> Unit,
    onSearchClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(alpha = 0.3f)))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Reader Settings", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        // THEME SELECTOR CARD
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderThemeCircle("Arrecife", Color(0xFFF8F9FA), theme == ReaderTheme.ARRECIFE) { onThemeChange(ReaderTheme.ARRECIFE) }
                ReaderThemeCircle("Creta", Color(0xFFF4ECD8), theme == ReaderTheme.CRETA) { onThemeChange(ReaderTheme.CRETA) }
                ReaderThemeCircle("Papirus", Color(0xFFD2D2D2), theme == ReaderTheme.PAPIRUS) { onThemeChange(ReaderTheme.PAPIRUS) }
                ReaderThemeCircle("Abisal", Color(0xFF011627), theme == ReaderTheme.ABISAL) { onThemeChange(ReaderTheme.ABISAL) }
                ReaderThemeCircle("Onyx", Color.Black, theme == ReaderTheme.ONYX) { onThemeChange(ReaderTheme.ONYX) }
                
                VerticalDivider(modifier = Modifier.height(30.dp).padding(horizontal = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                
                // Voice Placeholder
                IconButton(onClick = {}) {
                    Icon(Icons.Default.GraphicEq, "Voice", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // TEXT CUSTOMIZE CARD
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1.3f).height(160.dp).clickable { onNavigateToAdvanced() }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Default.TextFields, null, modifier = Modifier.size(32.dp))
                    Column {
                        Text("Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Customize", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            // SMALL ACTION CARDS COLUMN
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallActionCard(Icons.Default.Search, "Buscar", onClick = onSearchClick)
                SmallActionCard(
                    if (isVerticalMode || isWebtoonMode) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                    "Orientation",
                    active = true,
                    onClick = { onVerticalModeChange(!isVerticalMode) }
                )
            }

            // VERTICAL SLIDERS
            Row(modifier = Modifier.weight(1.5f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerticalTouchSlider(
                    value = fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 60f..200f,
                    icon = Icons.Default.FormatSize,
                    label = "Size",
                    modifier = Modifier.weight(1f)
                )
                VerticalTouchSlider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.1f..1.0f,
                    icon = Icons.Default.LightMode,
                    label = "Bright",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Manga / Webtoon Mode Toggles for PDF
        if (isPdf) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChipToggle(isRtlEnabled, "Manga (RTL)", Icons.Default.MenuBook, Modifier.weight(1f)) { onRtlChange(it) }
                ChipToggle(isWebtoonMode, "Webtoon", Icons.Default.HistoryEdu, Modifier.weight(1f)) { onWebtoonChange(it) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AdvancedSettingsView(
    fontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    lineSpacing: Float,
    onLineSpacingChange: (Float) -> Unit,
    isSmartFitEnabled: Boolean,
    onSmartFitChange: (Boolean) -> Unit,
    isTextReflowEnabled: Boolean,
    onTextReflowChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentColor: Color
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text("Advanced Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // PREVIEW CARD
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(
                    "So we beat on, boats against the current, borne back ceaselessly into the past...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontFamily = when(fontFamily) {
                        "Lora", "Merriweather", "Serif" -> FontFamily.Serif
                        "Inter", "Sans" -> FontFamily.SansSerif
                        "Mono" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Select Font", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        
        val fontScrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(fontScrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("Original", "Lora", "Merriweather", "Inter", "Serif", "Sans", "Mono").forEach { font ->
                val isSelected = fontFamily == font
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(110.dp)
                        .clickable { onFontFamilyChange(font) }
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aa", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = when(font) {
                            "Lora", "Merriweather", "Serif" -> FontFamily.Serif
                            "Inter", "Sans" -> FontFamily.SansSerif
                            "Mono" -> FontFamily.Monospace
                            else -> FontFamily.Default
                        })
                        Text(font, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings with Switches
        AdvancedToggleRow("Smart Fit (Width)", isSmartFitEnabled) { onSmartFitChange(it) }
        AdvancedToggleRow("Text Reflow", isTextReflowEnabled) { onTextReflowChange(it) }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back")
        }
    }
}

@Composable
fun ReaderThemeCircle(name: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .clickable { onClick() }
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else Modifier)
            .then(if (isSelected) Modifier.padding(4.dp).background(color, androidx.compose.foundation.shape.CircleShape) else Modifier)
    )
}

@Composable
fun SmallActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit = {}) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, modifier = Modifier.size(24.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun VerticalTouchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .height(160.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val ratio = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    val newValue = valueRange.start + (valueRange.endInclusive - valueRange.start) * ratio
                    onValueChange(newValue)
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    val ratio = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    val newValue = valueRange.start + (valueRange.endInclusive - valueRange.start) * ratio
                    onValueChange(newValue)
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
            ) {
                val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ChipToggle(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable { onToggle(!selected) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AdvancedToggleRow(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = selected, onCheckedChange = onToggle)
    }
}

// --- EPUB IN-BOOK SEARCH ENGINE & UI ---

data class EpubSearchResult(
    val chapterIndex: Int,
    val chapterName: String,
    val previewText: String,
    val matchWord: String
)

suspend fun searchInEpub(
    filePath: String,
    chapterPaths: List<String>,
    query: String
): List<EpubSearchResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<EpubSearchResult>()
    if (query.isBlank()) return@withContext results
    
    try {
        val zip = java.util.zip.ZipFile(java.io.File(filePath))
        // 1. Obtener la ruta base OPF del contenedor
        val containerEntry = zip.getEntry("META-INF/container.xml")
        val opfDir = if (containerEntry != null) {
            val cxml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val opfP = """<rootfile[^>]+full-path="([^"]+)"""".toRegex().find(cxml)?.groupValues?.get(1) ?: ""
            if (opfP.contains("/")) opfP.substringBeforeLast("/") + "/" else ""
        } else ""

        chapterPaths.forEachIndexed { index, relPath ->
            val decodedPath = android.net.Uri.decode(relPath)
            val internalPath = opfDir + decodedPath
            val entry = zip.getEntry(internalPath) ?: zip.getEntry(decodedPath) ?: zip.getEntry(relPath)
            if (entry != null) {
                val rawHtml = zip.getInputStream(entry).bufferedReader().readText()
                val bodyText = Regex("(?si)<body[^>]*>(.*?)</body>").find(rawHtml)?.groupValues?.get(1) ?: rawHtml
                val cleanText = bodyText
                    .replace(Regex("(?s)<script.*?</script>"), "")
                    .replace(Regex("(?s)<style.*?</style>"), "")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("&nbsp;"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                var startIndex = 0
                while (true) {
                    val foundIndex = cleanText.indexOf(query, startIndex, ignoreCase = true)
                    if (foundIndex == -1) break
                    
                    val snippetStart = maxOf(0, foundIndex - 40)
                    val snippetEnd = minOf(cleanText.length, foundIndex + query.length + 40)
                    val prefix = if (snippetStart > 0) "..." else ""
                    val suffix = if (snippetEnd < cleanText.length) "..." else ""
                    
                    val rawSnippet = cleanText.substring(snippetStart, snippetEnd)
                    val preview = prefix + rawSnippet.trim() + suffix
                    
                    val chapterName = decodedPath.substringAfterLast("/").substringBeforeLast(".")
                        .replace("_", " ")
                        .replace("-", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

                    results.add(
                        EpubSearchResult(
                            chapterIndex = index,
                            chapterName = "Capítulo ${index + 1}: $chapterName",
                            previewText = preview,
                            matchWord = query
                        )
                    )
                    
                    startIndex = foundIndex + query.length
                    if (results.size >= 150) break
                }
            }
            if (results.size >= 150) return@forEachIndexed
        }
        zip.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext results
}

@Composable
fun HighlightedSearchText(text: String, query: String, color: Color) {
    val annotatedString = remember(text, query) {
        androidx.compose.ui.text.buildAnnotatedString {
            var startIndex = 0
            while (true) {
                val foundIndex = text.indexOf(query, startIndex, ignoreCase = true)
                if (foundIndex == -1) {
                    append(text.substring(startIndex))
                    break
                }
                
                append(text.substring(startIndex, foundIndex))
                
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontWeight = FontWeight.Bold,
                        background = color.copy(alpha = 0.25f),
                        color = color
                    )
                )
                append(text.substring(foundIndex, foundIndex + query.length))
                pop()
                
                startIndex = foundIndex + query.length
            }
        }
    }
    Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInBookDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    filePath: String,
    chapterPaths: List<String>,
    onResultClick: (Int) -> Unit
) {
    if (!show) return

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EpubSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buscar en el libro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de texto de búsqueda
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Escribe una frase o palabra...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                isSearching = true
                                scope.launch {
                                    results = searchInEpub(filePath, chapterPaths, query)
                                    isSearching = false
                                }
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cantidad de coincidencias
                if (!isSearching && results.isNotEmpty()) {
                    Text(
                        text = "Coincidencias encontradas (${results.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Área principal de resultados
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Buscando en los capítulos...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                    } else if (results.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (query.isEmpty()) "Ingresa una palabra para buscar" else "No se encontraron resultados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (query.isEmpty()) "Busca esa frase especial que se te haya quedado en la memoria." else "Intenta con términos más cortos o revisa la ortografía.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(results.size) { index ->
                                val result = results[index]
                                Surface(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onResultClick(result.chapterIndex)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = result.chapterName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        HighlightedSearchText(
                                            text = result.previewText,
                                            query = result.matchWord,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

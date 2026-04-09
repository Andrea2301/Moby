package com.example.moby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moby.data.db.MobyDatabase
import com.example.moby.models.Publication
import com.example.moby.models.PublicationFormat
import com.example.moby.logic.readers.PdfReaderComponent
import com.example.moby.logic.readers.CbzReaderComponent
import com.example.moby.logic.readers.EpubReaderComponent
import com.example.moby.data.PreferencesManager // 🧠 Import memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReaderTheme {
    ARRECIFE,
    CRETA,
    PAPIRUS,
    ABISAL
}

@Composable
fun ReaderScreen(
    publicationId: String, 
    onBack: () -> Unit,
    isAbisal: Boolean,
    preferencesManager: PreferencesManager // 🧠 Injected memory
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
            }
        }
    }

    val isDark = readerTheme == ReaderTheme.ABISAL
    val backgroundColor = when (readerTheme) {
        ReaderTheme.ARRECIFE -> Color(0xFFF8F9FA)
        ReaderTheme.CRETA -> Color(0xFFF4ECD8)
        ReaderTheme.PAPIRUS -> Color(0xFFD2D2D2)
        ReaderTheme.ABISAL -> Color(0xFF011627)
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

    // 🚀 DYNAMIC SYSTEM BARS COLOR: Match the reader theme
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
            dao.updatePublication(pub.copy(currentPosition = page, lastRead = System.currentTimeMillis()))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // 1. READER CONTENT LAYER
        Box(modifier = Modifier.fillMaxSize()) {
            when (pub.format) {
                PublicationFormat.PDF -> {
                    PdfReaderComponent(
                        filePath = pub.filePath,
                        initialPage = pub.currentPosition,
                        onPageChanged = progressUpdateHandler,
                        onTotalPagesReady = { totalPages = it },
                        isVerticalMode = pub.isVerticalMode,
                        theme = readerTheme,
                        onCenterTap = {
                            if (showSettings || showChapterList) {
                                showSettings = false; showChapterList = false
                            } else showControls = !showControls
                        }
                    )
                }
                PublicationFormat.EPUB -> {
                    EpubReaderComponent(
                        publicationId = pub.id,
                        filePath = pub.filePath,
                        initialChapter = pub.currentPosition,
                        onChapterChanged = progressUpdateHandler,
                        onVirtualPageChanged = { index, count -> virtualPageIndex = index; virtualPageCount = count },
                        onTotalChaptersReady = { totalPages = it },
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        lineSpacing = lineSpacing,
                        isVerticalMode = pub.isVerticalMode,
                        theme = readerTheme,
                        onCenterTap = {
                            if (showSettings || showChapterList) {
                                showSettings = false; showChapterList = false
                            } else showControls = !showControls
                        }
                    )
                }
                PublicationFormat.CBZ -> {
                    CbzReaderComponent(
                        filePath = pub.filePath,
                        initialPage = pub.currentPosition,
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (pub.format == PublicationFormat.EPUB) "Capítulos" else "Páginas", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp).statusBarsPadding()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
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
                        Text(if (pub.isVerticalMode) "Vertical" else "Horizontal", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
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

        // 🌟 SETTINGS HUD
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
                }
            )
        }

        
        // Progress Indicator
        if (!showControls && !showSettings) {
            Text(
                text = "${currentPage + 1} / $totalPages",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).navigationBarsPadding(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(0.4f) else Color.Black.copy(0.4f)
            )
        }
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
    onBrightnessChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            Text("Ajustes de Lectura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // THEME SELECTOR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReaderThemeItem("Arrecife", Color(0xFFF8F9FA), theme == ReaderTheme.ARRECIFE) { onThemeChange(ReaderTheme.ARRECIFE) }
                ReaderThemeItem("Creta", Color(0xFFF4ECD8), theme == ReaderTheme.CRETA) { onThemeChange(ReaderTheme.CRETA) }
                ReaderThemeItem("Papirus", Color(0xFFD2D2D2), theme == ReaderTheme.PAPIRUS) { onThemeChange(ReaderTheme.PAPIRUS) }
                ReaderThemeItem("Abisal", Color(0xFF011627), theme == ReaderTheme.ABISAL) { onThemeChange(ReaderTheme.ABISAL) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

            // FONT FAMILY
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Serif", "Sans", "Mono").forEach { font ->
                    FilterChip(
                        selected = fontFamily == font,
                        onClick = { onFontFamilyChange(font) },
                        label = { Text(font) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // FONT SIZE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, "Small", Modifier.size(16.dp))
                Slider(value = fontSize, onValueChange = onFontSizeChange, valueRange = 60f..200f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Icon(Icons.Default.TextFields, "Large", Modifier.size(24.dp))
            }

            // LINE SPACING
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatLineSpacing, "Spacing", Modifier.size(20.dp))
                Slider(value = lineSpacing, onValueChange = onLineSpacingChange, valueRange = 1.0f..2.5f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text("${lineSpacing.toString().take(3)}x", style = MaterialTheme.typography.bodySmall)
            }

            // BRIGHTNESS
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LightMode, "Brightness", Modifier.size(20.dp))
                Slider(value = brightness, onValueChange = onBrightnessChange, valueRange = 0.1f..1.0f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
fun ReaderThemeItem(name: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(color)
                .then(if (isSelected) Modifier.background(Color.Blue.copy(alpha = 0.2f)) else Modifier)
                .then(if (isSelected) Modifier.padding(2.dp).background(color, RoundedCornerShape(10.dp)) else Modifier)
        )
        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

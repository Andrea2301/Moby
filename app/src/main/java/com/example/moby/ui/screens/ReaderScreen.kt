package com.example.moby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.moby.data.PreferencesManager
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
    var isSmartFitEnabled: Boolean by remember { mutableStateOf(false) }
    var isTextReflowEnabled: Boolean by remember { mutableStateOf(false) }

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
                        isVerticalMode = pub.isVerticalMode,
                        theme = readerTheme,
                        isSmartFitEnabled = isSmartFitEnabled,
                        isTextReflowEnabled = isTextReflowEnabled,
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
                        }
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
                        Text(
                            text = if (pub.format == PublicationFormat.EPUB) "Cap. ${currentPage + 1}" else "Pág. ${currentPage + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${((currentPage.toFloat() / (totalPages - 1).coerceAtLeast(1)) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "$totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { progressUpdateHandler(it.toInt()) },
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
                onSmartFitChange = { isSmartFitEnabled = it },
                isTextReflowEnabled = isTextReflowEnabled,
                onTextReflowChange = { enabled ->
                    isTextReflowEnabled = enabled
                    scope.launch(Dispatchers.IO) {
                        dao.updateTextReflowEnabled(pub.id, enabled)
                    }
                },
                isPdf = pub.format == PublicationFormat.PDF
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
    onBrightnessChange: (Float) -> Unit,
    isSmartFitEnabled: Boolean = false,
    onSmartFitChange: (Boolean) -> Unit = {},
    isTextReflowEnabled: Boolean = false,
    onTextReflowChange: (Boolean) -> Unit = {},
    isPdf: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Ajustes de Lectura",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isPdf) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ajustar al ancho (Smart Fit)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isSmartFitEnabled, onCheckedChange = onSmartFitChange)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Texto (Reflow)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Extrae el texto para ajustar tamaño y fuentes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isTextReflowEnabled, onCheckedChange = onTextReflowChange)
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            }

            // THEME SELECTOR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReaderThemeItem("Arrecife", Color(0xFFF8F9FA), theme == ReaderTheme.ARRECIFE) { onThemeChange(ReaderTheme.ARRECIFE) }
                ReaderThemeItem("Creta", Color(0xFFF4ECD8), theme == ReaderTheme.CRETA) { onThemeChange(ReaderTheme.CRETA) }
                ReaderThemeItem("Papirus", Color(0xFFD2D2D2), theme == ReaderTheme.PAPIRUS) { onThemeChange(ReaderTheme.PAPIRUS) }
                ReaderThemeItem("Abisal", Color(0xFF011627), theme == ReaderTheme.ABISAL) { onThemeChange(ReaderTheme.ABISAL) }
            }

            if (!isPdf || isTextReflowEnabled) {
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
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

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

package com.example.moby.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.moby.data.db.PublicationDao
import com.example.moby.logic.BookMetadataExtractor
import com.example.moby.models.Publication
import com.example.moby.ui.components.PublicationCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import java.io.File

enum class SortBy {
    TITLE, AUTHOR, DATE_ADDED
}

enum class ReadingStatus {
    UNREAD, READING, FINISHED
}

@Composable
fun LibraryAdvancedFilterPanel(
    sortBy: SortBy,
    onSortChange: (SortBy) -> Unit,
    readingFilters: Set<ReadingStatus>,
    onReadingFiltersChange: (Set<ReadingStatus>) -> Unit,
    viewMode: com.example.moby.data.LibraryViewMode,
    onViewModeChange: (com.example.moby.data.LibraryViewMode) -> Unit,
    selectedFormat: com.example.moby.models.PublicationFormat?,
    onFormatChange: (com.example.moby.models.PublicationFormat?) -> Unit,
    groupByAuthor: Boolean,
    onGroupByAuthorChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp, top = 8.dp)
    ) {
        Text(
            "Configuración de Biblioteca",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            // ORDENADO POR
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ordenado por",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                SortBy.entries.forEach { option ->
                    FilterChip(
                        selected = sortBy == option,
                        onClick = { onSortChange(option) },
                        label = { Text(option.name.replace("_", " ").lowercase().capitalize()) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // VISTA
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Vista",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewModeIcon(
                        icon = Icons.Default.GridView,
                        selected = viewMode == com.example.moby.data.LibraryViewMode.GRID,
                        onClick = { onViewModeChange(com.example.moby.data.LibraryViewMode.GRID) }
                    )
                    ViewModeIcon(
                        icon = Icons.Default.ViewList,
                        selected = viewMode == com.example.moby.data.LibraryViewMode.LIST,
                        onClick = { onViewModeChange(com.example.moby.data.LibraryViewMode.LIST) }
                    )
                    ViewModeIcon(
                        icon = Icons.Default.TableRows,
                        selected = viewMode == com.example.moby.data.LibraryViewMode.SHELF,
                        onClick = { onViewModeChange(com.example.moby.data.LibraryViewMode.SHELF) }
                    )
                    ViewModeIcon(
                        icon = Icons.Default.AutoAwesome,
                        selected = viewMode == com.example.moby.data.LibraryViewMode.GENRES,
                        onClick = { onViewModeChange(com.example.moby.data.LibraryViewMode.GENRES) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ESTADO DE LECTURA
        Text(
            "Estado de lectura",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReadingStatus.entries.forEach { status ->
                val isSelected = status in readingFilters
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newFilters = if (isSelected) readingFilters - status else readingFilters + status
                        if (newFilters.isNotEmpty()) onReadingFiltersChange(newFilters)
                    },
                    label = { Text(status.name.lowercase().capitalize()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // FORMATO
        Text(
            "Formato",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatChip(label = "Todos", selected = selectedFormat == null, onClick = { onFormatChange(null) })
            com.example.moby.models.PublicationFormat.entries.forEach { format ->
                FormatChip(
                    label = format.name,
                    selected = selectedFormat == format,
                    onClick = { onFormatChange(format) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AGRUPAR POR AUTOR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Agrupar por autor",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = groupByAuthor,
                onCheckedChange = onGroupByAuthorChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    publicationDao: PublicationDao,
    metadataExtractor: BookMetadataExtractor,
    preferencesManager: com.example.moby.data.PreferencesManager,
    searchQuery: String,
    onNavigate: (com.example.moby.MobyScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    val viewMode by preferencesManager.libraryViewModeFlow.collectAsState(initial = com.example.moby.data.LibraryViewMode.GRID)
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFormat by remember { mutableStateOf<com.example.moby.models.PublicationFormat?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var sortBy by remember { mutableStateOf(SortBy.DATE_ADDED) }
    var readingFilters by remember {
        mutableStateOf(
            setOf(
                ReadingStatus.UNREAD,
                ReadingStatus.READING,
                ReadingStatus.FINISHED
            )
        )
    }
    var groupByAuthor by remember { mutableStateOf(false) }

    var publicationToEdit by remember { mutableStateOf<com.example.moby.models.Publication?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    val coverSearchService = remember { com.example.moby.logic.CoverSearchService(context) }
    var showWebCoverSearch by remember { mutableStateOf(false) }

    var showImportSheet by remember { mutableStateOf(false) }
    var pendingFormats by remember { mutableStateOf(setOf<String>()) }
    var pendingMinSize by remember { mutableLongStateOf(0L) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            scope.launch {
                snackbarHostState.showSnackbar("Escaneando carpeta...")
                
                val importedCount = withContext(Dispatchers.IO) {
                    var count = 0
                    val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                    pickedDir?.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val name = file.name ?: ""
                            val ext = name.substringAfterLast(".", "").uppercase()
                            val size = file.length()
                            
                            if (ext in pendingFormats && size >= pendingMinSize * 1024) {
                                // DETECCIÓN DE DUPLICADOS
                                val existing = publicationDao.getPublicationById(name)
                                if (existing == null) {
                                    val newPublication = metadataExtractor.extract(file.uri, name)
                                    if (newPublication != null) {
                                        publicationDao.insertPublication(newPublication)
                                        count++
                                    }
                                }
                            }
                        }
                    }
                    count
                }
                
                snackbarHostState.showSnackbar("Escaneo finalizado: $importedCount nuevos libros añadidos")
            }
        }
    }

    val coverPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { sourceUri ->
                publicationToEdit?.let { pub ->
                    scope.launch {
                        try {
                            val coverDir = File(context.filesDir, "covers")
                            if (!coverDir.exists()) coverDir.mkdirs()

                            val coverFile = File(coverDir, "cover_${pub.id}.jpg")
                            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                coverFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            val updatedPub = pub.copy(coverUrl = coverFile.absolutePath)
                            publicationDao.updatePublication(updatedPub)
                            snackbarHostState.showSnackbar("Portada actualizada")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Error al guardar la portada")
                        } finally {
                            publicationToEdit = null
                        }
                    }
                }
            }
        }

    val filteredPublications =
        remember(publications, searchQuery, selectedFormat, sortBy, readingFilters) {
            publications.filter { pub ->
                // Búsqueda
                val matchesSearch = searchQuery.isEmpty() || pub.title.contains(
                    searchQuery,
                    ignoreCase = true
                ) || pub.author.contains(searchQuery, ignoreCase = true)
                // Formato
                val matchesFormat = selectedFormat == null || pub.format == selectedFormat
                // Estado de lectura
                val status = when {
                    pub.currentPosition == 0 -> ReadingStatus.UNREAD
                    pub.currentPosition >= pub.totalPages && pub.totalPages > 0 -> ReadingStatus.FINISHED
                    else -> ReadingStatus.READING
                }
                val matchesStatus = status in readingFilters

                matchesSearch && matchesFormat && matchesStatus
            }.let { list ->
                // Ordenamiento
                when (sortBy) {
                    SortBy.TITLE -> list.sortedBy { it.title }
                    SortBy.AUTHOR -> list.sortedBy { it.author }
                    SortBy.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
                }
            }
        }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar("Importando ${uris.size} libros...")
                var importedCount = 0

                uris.forEach { uri ->
                    val fileName =
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: "libro_${System.currentTimeMillis()}"

                    // DETECCIÓN DE DUPLICADOS (En importación masiva, saltamos los duplicados silenciosamente para no saturar)
                    val existing = publicationDao.getPublicationById(fileName)
                    if (existing == null) {
                        val newPublication = metadataExtractor.extract(uri, fileName)
                        if (newPublication != null) {
                            publicationDao.insertPublication(newPublication)
                            importedCount++
                        }
                    }
                }

                snackbarHostState.showSnackbar("Importación finalizada: $importedCount nuevos libros añadidos")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val bgStart = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF8F9FA)
        val bgEnd = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFECF0F3)
        
        val dynamicGradient = Brush.verticalGradient(
            colors = listOf(
                bgStart,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), // Sutil brillo cian
                bgEnd
            )
        )

        Box(modifier = Modifier.fillMaxSize().background(dynamicGradient).padding(padding)) {
            if (publications.isEmpty()) {
                PlaceholderScreen(
                    title = "Biblioteca Vacía",
                    subtitle = "Usa el botón '+' para importar tus primeros libros (PDF, EPUB, CBZ)."
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${publications.size} libros",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // BOTÓN DE FILTRO AVANZADO
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "Filtrar",
                                    tint = if (selectedFormat != null || sortBy != SortBy.DATE_ADDED || readingFilters.size < 3)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }

                            // BOTÓN DE VISTA (Solo icono rápido, el completo está en el panel)
                            IconButton(onClick = {
                                scope.launch {
                                    val nextMode = when (viewMode) {
                                        com.example.moby.data.LibraryViewMode.GRID -> com.example.moby.data.LibraryViewMode.SHELF
                                        com.example.moby.data.LibraryViewMode.SHELF -> com.example.moby.data.LibraryViewMode.LIST
                                        com.example.moby.data.LibraryViewMode.LIST -> com.example.moby.data.LibraryViewMode.GENRES
                                        com.example.moby.data.LibraryViewMode.GENRES -> com.example.moby.data.LibraryViewMode.GRID
                                    }
                                    preferencesManager.setLibraryViewMode(nextMode)
                                }
                            }) {
                                Icon(
                                    imageVector = when (viewMode) {
                                        com.example.moby.data.LibraryViewMode.GRID -> Icons.Filled.GridView
                                        com.example.moby.data.LibraryViewMode.SHELF -> Icons.Filled.TableRows
                                        com.example.moby.data.LibraryViewMode.LIST -> Icons.Filled.ViewList
                                        com.example.moby.data.LibraryViewMode.GENRES -> Icons.Filled.AutoAwesome
                                    },
                                    contentDescription = "Cambiar Vista Style"
                                )
                            }
                        }
                    }

                    if (viewMode == com.example.moby.data.LibraryViewMode.GENRES) {
                        val publicationsByGenre = remember(filteredPublications) {
                            filteredPublications.groupBy { it.genre }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(publicationsByGenre.keys.toList()) { genre ->
                                com.example.moby.ui.components.GenreShelf(
                                    genre = genre ?: "Desconocido",
                                    books = publicationsByGenre[genre] ?: emptyList(),
                                    onBookClick = { onNavigate(com.example.moby.MobyScreen.Reader(it.id)) },
                                    onLongClick = {
                                        publicationToEdit = it
                                        showContextMenu = true
                                    }
                                )
                            }
                        }
                    } else {
                        val groupedPublications = remember(filteredPublications, groupByAuthor) {
                            if (groupByAuthor) {
                                filteredPublications.groupBy { it.author }
                            } else {
                                mapOf("Todos los libros" to filteredPublications)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            groupedPublications.forEach { (author, books) ->
                                if (groupByAuthor) {
                                    stickyHeader {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                        ) {
                                            Text(
                                                text = author,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 12.dp
                                                )
                                            )
                                        }
                                    }
                                }

                                when (viewMode) {
                                    com.example.moby.data.LibraryViewMode.LIST -> {
                                        items(books) { pub ->
                                            com.example.moby.ui.components.PublicationListItem(
                                                publication = pub,
                                                onClick = { onNavigate(com.example.moby.MobyScreen.Reader(pub.id)) },
                                                onLongClick = { 
                                                    publicationToEdit = pub
                                                    showContextMenu = true
                                                }
                                            )
                                        }
                                    }
                                    com.example.moby.data.LibraryViewMode.GRID -> {
                                        val itemsPerRow = 3
                                        val chunked = books.chunked(itemsPerRow)
                                        items(chunked) { rowBooks ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                rowBooks.forEach { pub ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        PublicationCard(
                                                            publication = pub,
                                                            onClick = { onNavigate(com.example.moby.MobyScreen.Reader(pub.id)) },
                                                            onMenuClick = { 
                                                                publicationToEdit = pub
                                                                showContextMenu = true
                                                            }
                                                        )
                                                    }
                                                }
                                                repeat(itemsPerRow - rowBooks.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                    com.example.moby.data.LibraryViewMode.SHELF -> {
                                        val rows = books.chunked(3)
                                        items(rows) { rowBooks ->
                                            com.example.moby.ui.components.LibraryShelf(
                                                books = rowBooks,
                                                onBookClick = { onNavigate(com.example.moby.MobyScreen.Reader(it.id)) },
                                                onMenuClick = { 
                                                    publicationToEdit = it
                                                    showContextMenu = true
                                                }
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

            } // Cierra el else de publications.isEmpty()
            
            var fabExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (fabExpanded) {
                    if (publications.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            text = { Text("Limpiar Biblioteca") },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = "Clean") },
                            onClick = {
                                scope.launch { publicationDao.deleteAllPublications() }
                                fabExpanded = false
                            },
                            modifier = Modifier.padding(bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    ExtendedFloatingActionButton(
                        text = { Text("Importar Libro") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Import") },
                        onClick = {
                            showImportSheet = true
                            fabExpanded = false
                        },
                        modifier = Modifier.padding(bottom = 16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "Opciones"
                    )
                }
            }
        }

        if (showContextMenu && publicationToEdit != null) {
            com.example.moby.ui.components.BookDetailsSheet(
                publication = publicationToEdit!!,
                onReadClick = {
                    showContextMenu = false
                    onNavigate(com.example.moby.MobyScreen.Reader(publicationToEdit!!.id))
                    publicationToEdit = null
                },
                onChangeCoverClick = {
                    showContextMenu = false
                    coverPickerLauncher.launch("image/*")
                },
                onWebSearchClick = {
                    showContextMenu = false
                    showWebCoverSearch = true
                },
                onDeleteClick = {
                    scope.launch {
                        publicationDao.deletePublication(publicationToEdit!!)
                        showContextMenu = false
                        publicationToEdit = null
                    }
                },
                onDismiss = {
                    showContextMenu = false
                    publicationToEdit = null
                }
            )
        }

        if (showImportSheet) {
            com.example.moby.ui.components.SmartImportSheet(
                onDismiss = { showImportSheet = false },
                onScanFolderClick = { formats, minSize ->
                    pendingFormats = formats
                    pendingMinSize = minSize
                    folderPickerLauncher.launch(null)
                    showImportSheet = false
                },
                onSelectFilesClick = { formats ->
                    val mimeTypes = mutableListOf<String>()
                    if (formats.contains("EPUB")) mimeTypes.add("application/epub+zip")
                    if (formats.contains("PDF")) mimeTypes.add("application/pdf")
                    if (formats.contains("MOBI")) mimeTypes.add("application/octet-stream")
                    if (formats.contains("CBZ")) mimeTypes.add("application/x-cbz")
                    if (formats.contains("TXT")) mimeTypes.add("text/plain")
                    
                    if (mimeTypes.isEmpty()) {
                        mimeTypes.addAll(listOf(
                            "application/pdf",
                            "application/epub+zip",
                            "application/x-cbz",
                            "application/octet-stream"
                        ))
                    }
                    
                    launcher.launch(mimeTypes.toTypedArray())
                    showImportSheet = false
                }
            )
        }

        if (showWebCoverSearch && publicationToEdit != null) {
            com.example.moby.ui.components.WebCoverBrowserDialog(
                initialQuery = "${publicationToEdit!!.title} ${publicationToEdit!!.author}",
                onDismiss = { showWebCoverSearch = false },
                onImageSelected = { coverUrl ->
                    scope.launch {
                        val localPath =
                            coverSearchService.downloadCover(coverUrl, publicationToEdit!!.id)
                        if (localPath != null) {
                            val updatedPub = publicationToEdit!!.copy(coverUrl = localPath)
                            publicationDao.updatePublication(updatedPub)
                            snackbarHostState.showSnackbar("Portada actualizada")
                        } else {
                            snackbarHostState.showSnackbar("Error al descargar la portada")
                        }
                        showWebCoverSearch = false
                        publicationToEdit = null
                    }
                }
            )
        }

        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                LibraryAdvancedFilterPanel(
                    sortBy = sortBy,
                    onSortChange = { newSort -> sortBy = newSort },
                    readingFilters = readingFilters,
                    onReadingFiltersChange = { newFilters -> readingFilters = newFilters },
                    viewMode = viewMode,
                    onViewModeChange = { newMode ->
                        scope.launch { preferencesManager.setLibraryViewMode(newMode) }
                    },
                    selectedFormat = selectedFormat,
                    onFormatChange = { newFormat -> selectedFormat = newFormat },
                    groupByAuthor = groupByAuthor,
                    onGroupByAuthorChange = { newValue -> groupByAuthor = newValue }
                )
            }
        }
    }
}

@Composable
private fun ViewModeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = androidx.compose.ui.Modifier
            .size(40.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = androidx.compose.ui.Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun FilterOption(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onCheckedChange)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = androidx.compose.ui.Modifier.padding(start = 8.dp)
        )
    }
}



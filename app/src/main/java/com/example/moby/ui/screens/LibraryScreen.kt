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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.moby.data.db.PublicationDao
import com.example.moby.logic.BookMetadataExtractor
import com.example.moby.models.Publication
import com.example.moby.ui.components.PublicationCard
import kotlinx.coroutines.launch
import java.io.File

enum class SortBy {
    TITLE, AUTHOR, DATE_ADDED
}

enum class ReadingStatus {
    UNREAD, READING, FINISHED
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
    var readingFilters by remember { mutableStateOf(setOf(ReadingStatus.UNREAD, ReadingStatus.READING, ReadingStatus.FINISHED)) }
    var groupByAuthor by remember { mutableStateOf(false) }

    var publicationToEdit by remember { mutableStateOf<com.example.moby.models.Publication?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    
    val coverSearchService = remember { com.example.moby.logic.CoverSearchService(context) }
    var showWebCoverSearch by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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

    val filteredPublications = remember(publications, searchQuery, selectedFormat, sortBy, readingFilters) {
        publications.filter { pub ->
            // Búsqueda
            val matchesSearch = searchQuery.isEmpty() || pub.title.contains(searchQuery, ignoreCase = true) || pub.author.contains(searchQuery, ignoreCase = true)
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
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (publications.isEmpty()) {
                PlaceholderScreen(
                    title = "Biblioteca Vacía",
                    subtitle = "Usa el botón '+' para importar tus primeros libros (PDF, EPUB, CBZ)."
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                    val nextMode = when(viewMode) {
                                        com.example.moby.data.LibraryViewMode.GRID -> com.example.moby.data.LibraryViewMode.SHELF
                                        com.example.moby.data.LibraryViewMode.SHELF -> com.example.moby.data.LibraryViewMode.LIST
                                        com.example.moby.data.LibraryViewMode.LIST -> com.example.moby.data.LibraryViewMode.GRID
                                    }
                                    preferencesManager.setLibraryViewMode(nextMode)
                                }
                            }) {
                                Icon(
                                    imageVector = when(viewMode) {
                                        com.example.moby.data.LibraryViewMode.GRID -> Icons.Filled.GridView
                                        com.example.moby.data.LibraryViewMode.SHELF -> Icons.Filled.TableRows
                                        com.example.moby.data.LibraryViewMode.LIST -> Icons.Filled.ViewList
                                    },
                                    contentDescription = "Cambiar Vista Style"
                                )
                            }
                        }
                    }
                    
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
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                }
                            }

                            when (viewMode) {
                                com.example.moby.data.LibraryViewMode.LIST -> {
                                    items(books) { publication ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            com.example.moby.ui.components.PublicationListItem(
                                                publication = publication,
                                                onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) },
                                                onLongClick = { 
                                                    publicationToEdit = publication
                                                    showContextMenu = true
                                                }
                                            )
                                        }
                                    }
                                }
                                com.example.moby.data.LibraryViewMode.GRID -> {
                                    val rows = books.chunked(3)
                                    items(rows) { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            rowItems.forEach { publication ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PublicationCard(
                                                        publication = publication,
                                                        onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) },
                                                        onLongClick = { 
                                                            publicationToEdit = publication
                                                            showContextMenu = true
                                                        }
                                                    )
                                                }
                                            }
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                                com.example.moby.data.LibraryViewMode.SHELF -> {
                                    val rows = books.chunked(3)
                                    items(rows) { rowItems ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            // Estante
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().height(24.dp)
                                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)
                                                        ))
                                                )
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                                        .background(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                                )
                                            }
                                            // Libros
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                rowItems.forEach { publication ->
                                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                        Card(
                                                            modifier = Modifier.width(90.dp).height(130.dp)
                                                                .combinedClickable(
                                                                    onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) },
                                                                    onLongClick = { 
                                                                        publicationToEdit = publication
                                                                        showContextMenu = true
                                                                    }
                                                                ),
                                                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
                                                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                                        ) {
                                                            coil.compose.AsyncImage(
                                                                model = publication.coverUrl,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            var fabExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (fabExpanded) {
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
                    
                    ExtendedFloatingActionButton(
                        text = { Text("Importar Libro") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Import") },
                        onClick = { 
                            launcher.launch(arrayOf(
                                "application/pdf", 
                                "application/epub+zip", 
                                "application/x-cbz",
                                "application/zip",
                                "application/octet-stream"
                            ))
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
    }

    if (showContextMenu && publicationToEdit != null) {
        AlertDialog(
            onDismissRequest = { 
                showContextMenu = false
                publicationToEdit = null
            },
            title = { Text(publicationToEdit?.title ?: "Opciones") },
            text = { Text("¿Qué deseas hacer con este libro?") },
            confirmButton = {
                Column {
                    TextButton(onClick = { 
                        showContextMenu = false
                        coverPickerLauncher.launch("image/*")
                    }) {
                        Text("Elegir de Galería")
                    }
                    TextButton(onClick = { 
                        showContextMenu = false
                        showWebCoverSearch = true
                    }) {
                        Text("Buscar en la Web")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    scope.launch {
                        publicationToEdit?.let { publicationDao.deletePublication(it) }
                        showContextMenu = false
                        publicationToEdit = null
                    }
                }) {
                    Text("Eliminar Libro", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showWebCoverSearch && publicationToEdit != null) {
        WebCoverSearchDialog(
            publication = publicationToEdit!!,
            searchService = coverSearchService,
            onDismiss = { showWebCoverSearch = false },
            onCoverSelected = { coverUrl ->
                scope.launch {
                    val localPath = coverSearchService.downloadCover(coverUrl, publicationToEdit!!.id)
                    if (localPath != null) {
                        val updatedPub = publicationToEdit!!.copy(coverUrl = localPath)
                        publicationDao.updatePublication(updatedPub)
                        snackbarHostState.showSnackbar("Portada actualizada desde la web")
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
            AdvancedFilterContent(
                sortBy = sortBy,
                onSortChange = { sortBy = it },
                readingFilters = readingFilters,
                onReadingFiltersChange = { readingFilters = it },
                viewMode = viewMode,
                onViewModeChange = { 
                    scope.launch { preferencesManager.setLibraryViewMode(it) }
                },
                selectedFormat = selectedFormat,
                onFormatChange = { selectedFormat = it },
                groupByAuthor = groupByAuthor,
                onGroupByAuthorChange = { groupByAuthor = it }
            )
        }
    }
}

@Composable
fun AdvancedFilterContent(
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
                Text("Ordenado por", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                SortOption("Título", sortBy == SortBy.TITLE) { onSortChange(SortBy.TITLE) }
                SortOption("Autor", sortBy == SortBy.AUTHOR) { onSortChange(SortBy.AUTHOR) }
                SortOption("Fecha", sortBy == SortBy.DATE_ADDED) { onSortChange(SortBy.DATE_ADDED) }
            }

            // FILTRO DE LECTURA
            Column(modifier = Modifier.weight(1f)) {
                Text("Filtro de lectura", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                FilterOption("Sin leer", ReadingStatus.UNREAD in readingFilters) {
                    val next = if (ReadingStatus.UNREAD in readingFilters) readingFilters - ReadingStatus.UNREAD else readingFilters + ReadingStatus.UNREAD
                    if (next.isNotEmpty()) onReadingFiltersChange(next)
                }
                FilterOption("Leyendo", ReadingStatus.READING in readingFilters) {
                    val next = if (ReadingStatus.READING in readingFilters) readingFilters - ReadingStatus.READING else readingFilters + ReadingStatus.READING
                    if (next.isNotEmpty()) onReadingFiltersChange(next)
                }
                FilterOption("Finalizado", ReadingStatus.FINISHED in readingFilters) {
                    val next = if (ReadingStatus.FINISHED in readingFilters) readingFilters - ReadingStatus.FINISHED else readingFilters + ReadingStatus.FINISHED
                    if (next.isNotEmpty()) onReadingFiltersChange(next)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // DISPOSICIÓN
        Text("Disposición", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LayoutToggle(Icons.Filled.ViewList, viewMode == com.example.moby.data.LibraryViewMode.LIST, Modifier.weight(1f)) {
                onViewModeChange(com.example.moby.data.LibraryViewMode.LIST)
            }
            LayoutToggle(Icons.Filled.GridView, viewMode == com.example.moby.data.LibraryViewMode.GRID, Modifier.weight(1f)) {
                onViewModeChange(com.example.moby.data.LibraryViewMode.GRID)
            }
            LayoutToggle(Icons.Filled.TableRows, viewMode == com.example.moby.data.LibraryViewMode.SHELF, Modifier.weight(1f)) {
                onViewModeChange(com.example.moby.data.LibraryViewMode.SHELF)
            }
        }

        Spacer(Modifier.height(24.dp))

        // TIPO DE ARCHIVO
        Text("Tipo de archivo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatChip("Todo", selectedFormat == null) { onFormatChange(null) }
            FormatChip("EPUB", selectedFormat == com.example.moby.models.PublicationFormat.EPUB) { onFormatChange(com.example.moby.models.PublicationFormat.EPUB) }
            FormatChip("PDF", selectedFormat == com.example.moby.models.PublicationFormat.PDF) { onFormatChange(com.example.moby.models.PublicationFormat.PDF) }
            FormatChip("CBZ", selectedFormat == com.example.moby.models.PublicationFormat.CBZ) { onFormatChange(com.example.moby.models.PublicationFormat.CBZ) }
        }

        Spacer(Modifier.height(24.dp))

        // AGRUPACIÓN
        Text("Agrupación", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        FilterOption("Agrupar por autor", groupByAuthor) { onGroupByAuthorChange(!groupByAuthor) }
    }
}

@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FilterOption(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onCheckedChange)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LayoutToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
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
fun WebCoverSearchDialog(
    publication: com.example.moby.models.Publication,
    searchService: com.example.moby.logic.CoverSearchService,
    onDismiss: () -> Unit,
    onCoverSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("${publication.title} ${publication.author}") }
    var covers by remember { mutableStateOf<List<com.example.moby.logic.WebCover>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isSearching = true
        covers = searchService.searchCovers(searchQuery)
        isSearching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar Portada Online") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Términos de búsqueda") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                isSearching = true
                                covers = searchService.searchCovers(searchQuery)
                                isSearching = false
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar")
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (covers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron portadas", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(covers.size) { index ->
                            val cover = covers[index]
                            Card(
                                onClick = { onCoverSelected(cover.largeUrl) },
                                modifier = Modifier.aspectRatio(0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                coil.compose.AsyncImage(
                                    model = cover.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


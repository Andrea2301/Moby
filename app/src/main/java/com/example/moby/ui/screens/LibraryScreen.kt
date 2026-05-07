package com.example.moby.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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

@OptIn(ExperimentalMaterial3Api::class)
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
    
    val filteredPublications = remember(publications, searchQuery) {
        if (searchQuery.isEmpty()) publications
        else publications.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || it.author.contains(searchQuery, ignoreCase = true)
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
                    
                    when (viewMode) {
                        com.example.moby.data.LibraryViewMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredPublications) { publication ->
                                    com.example.moby.ui.components.PublicationListItem(
                                        publication = publication,
                                        onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) }
                                    )
                                }
                            }
                        }
                        com.example.moby.data.LibraryViewMode.SHELF -> {
                            val chunkedPubs = filteredPublications.chunked(3)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                listItems(chunkedPubs) { rowItems ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 28.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(24.dp)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.background,
                                                                MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                        )
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                                                    )
                                            )
                                        }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            rowItems.forEach { publication ->
                                                Box(
                                                    modifier = Modifier.weight(1f), 
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Card(
                                                        onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) },
                                                        modifier = Modifier.width(90.dp).height(130.dp),
                                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                                    ) {
                                                        coil.compose.AsyncImage(
                                                            model = publication.coverUrl,
                                                            contentDescription = "Portada",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredPublications) { publication ->
                                    PublicationCard(
                                        publication = publication,
                                        onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) }
                                    )
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
}


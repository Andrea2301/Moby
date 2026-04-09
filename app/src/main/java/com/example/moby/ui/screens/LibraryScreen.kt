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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
    onNavigate: (com.example.moby.MobyScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    val viewMode by preferencesManager.libraryViewModeFlow.collectAsState(initial = com.example.moby.data.LibraryViewMode.GRID)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it) ?: "Unknown"
            scope.launch {
                val newPublication = metadataExtractor.extract(it, fileName)
                if (newPublication != null) {
                    publicationDao.insertPublication(newPublication)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            listItems(publications) { publication ->
                                com.example.moby.ui.components.PublicationListItem(
                                    publication = publication,
                                    onClick = { onNavigate(com.example.moby.MobyScreen.Reader(publication.id)) }
                                )
                            }
                        }
                    }
                    com.example.moby.data.LibraryViewMode.SHELF -> {
                        val chunkedPubs = publications.chunked(3)
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
                                    // El Estante virtual (Simulación 3D)
                                    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                                        // Profundidad del estante (Top Face)
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
                                        // Labio frontal del estante (Front Lip)
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
                            items(publications) { publication ->
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
                        launcher.launch("*/*")
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

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = it.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

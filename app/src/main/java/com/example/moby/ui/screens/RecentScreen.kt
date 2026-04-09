package com.example.moby.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.moby.MobyScreen
import com.example.moby.data.db.PublicationDao
import com.example.moby.ui.components.PublicationListItem

@Composable
fun RecentScreen(
    publicationDao: PublicationDao,
    onNavigate: (MobyScreen) -> Unit
) {
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    
    // Filter and sort by lastRead (most recent first)
    val recentPubs = remember(publications) {
        publications
            .filter { it.lastRead > 0 }
            .sortedByDescending { it.lastRead }
    }

    if (recentPubs.isEmpty()) {
        PlaceholderScreen("Recientes", "Tus lecturas más recientes aparecerán aquí.")
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Seguir leyendo",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentPubs) { publication ->
                    PublicationListItem(
                        publication = publication,
                        onClick = { onNavigate(MobyScreen.Reader(publication.id)) }
                    )
                }
            }
        }
    }
}

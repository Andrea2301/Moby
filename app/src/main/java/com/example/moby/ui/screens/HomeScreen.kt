package com.example.moby.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.moby.MobyScreen
import com.example.moby.data.db.PublicationDao
import com.example.moby.models.Publication

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier, 
    isAbisal: Boolean,
    publicationDao: PublicationDao,
    onNavigate: (MobyScreen) -> Unit
) {
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    
    val lastBook = remember(publications) {
        publications
            .filter { it.lastRead > 0 }
            .maxByOrNull { it.lastRead }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAbisal) "Profundidades Abisales" else "Arrecife de Luz",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Bienvenido a tu santuario de lectura minimalista.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        if (lastBook != null) {
            com.example.moby.ui.components.FeaturedBookCard(
                publication = lastBook,
                onContinueReading = { onNavigate(MobyScreen.Reader(lastBook.id)) }
            )
        } else {
            OutlinedButton(
                onClick = { onNavigate(MobyScreen.Library) },
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text("Explorar la Biblioteca")
            }
        }
    }
}

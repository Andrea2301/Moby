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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAbisal) "Profundidades Abisales" else "Arrecife de Luz",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (lastBook != null) 
                "Bienvenido de nuevo. ¿Continuamos con '${lastBook.title}'?" 
                else "Bienvenido a tu santuario de lectura minimalista.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        if (lastBook != null) {
            Button(
                onClick = { onNavigate(MobyScreen.Reader(lastBook.id)) },
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Continuar Lectura")
            }
        }
        
        OutlinedButton(
            onClick = { onNavigate(MobyScreen.Library) },
            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 12.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Ir a la Biblioteca")
        }
    }
}

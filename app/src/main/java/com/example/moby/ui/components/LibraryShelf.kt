package com.example.moby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moby.models.Publication

@Composable
fun LibraryShelf(
    books: List<Publication>,
    onBookClick: (Publication) -> Unit,
    onMenuClick: (Publication) -> Unit
) {
    // Detectamos el modo oscuro basándonos en si el fondo del tema es oscuro
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    
    // Paleta Adaptativa Refinada
    val shelfSlabColor = if (isDark) Color(0xFF2C2C2C) else Color.White
    val nicheBgColor = if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.85f) else Color(0xFFF2F2F2)
    val shelfFrontTop = if (isDark) Color(0xFF383838) else Color(0xFFF8F8F8)
    val shelfFrontBottom = if (isDark) Color(0xFF151515) else Color(0xFFD0D0D0)
    val edgeHighlight = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.8f)
    val shadowAlpha = if (isDark) 0.5f else 0.15f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. EL NICHO (Fondo continuo con profundidad lateral)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Fondo central (Usamos el color calculado)
            Box(modifier = Modifier.fillMaxSize().background(nicheBgColor))

            // SOMBRA SUPERIOR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = if (isDark) 0.5f else 0.12f), Color.Transparent)
                        )
                    )
            )

            // SOMBRAS LATERALES
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxHeight().width(44.dp).background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f), Color.Transparent))
                ))
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxHeight().width(44.dp).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f)))
                ))
            }
        }

        // 2. EL ESTANTE
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Superficie superior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(shelfSlabColor)
            )
            
            // Frente del estante
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(shelfFrontTop, shelfFrontBottom)
                        )
                    )
            ) {
                // Brillo de arista
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(edgeHighlight)
                )
            }

            // SOMBRA INFERIOR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = shadowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 3. LIBROS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .offset(y = (-34).dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom
        ) {
            books.forEach { book ->
                ShelfBookItem(
                    publication = book,
                    onClick = { onBookClick(book) },
                    onMenuClick = { onMenuClick(book) }
                )
            }
            repeat(3 - books.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ShelfBookItem(
    publication: Publication,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(2.dp),
                    ambientColor = Color.Black.copy(alpha = 0.6f),
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(2.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMenuClick
                )
        ) {
            AsyncImage(
                model = publication.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(90.dp)
                    .height(135.dp),
                contentScale = ContentScale.Crop
            )

            // Icono de Menú Flotante
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 6.dp, end = 2.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Barra de Progreso Sutil (Dorada/Cálida para madera)
            if (publication.totalPages > 0 && publication.currentPosition > 0) {
                LinearProgressIndicator(
                    progress = { publication.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Color(0xFFFFC107), // Ambar/Dorado
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
        
        // Sombra de contacto profunda
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(6.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

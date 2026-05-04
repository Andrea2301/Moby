package com.example.moby.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.moby.MobyScreen
import com.example.moby.data.db.PublicationDao
import com.example.moby.models.Publication
import kotlin.math.absoluteValue

// Generar color único basado en el ID del libro
fun generateColorFromBook(publication: Publication): Color {
    val hash = publication.id.hashCode().absoluteValue
    val hue = (hash % 360).toFloat()
    val saturation = 0.6f
    val lightness = 0.5f
    
    // Convertir HSL a RGB manualmente
    val c = (1 - (2 * lightness - 1).absoluteValue) * saturation
    val hPrime = hue / 60f
    val x = c * (1 - ((hPrime % 2) - 1).absoluteValue)
    
    val (rPrime, gPrime, bPrime) = when {
        hPrime < 1 -> Triple(c, x, 0f)
        hPrime < 2 -> Triple(x, c, 0f)
        hPrime < 3 -> Triple(0f, c, x)
        hPrime < 4 -> Triple(0f, x, c)
        hPrime < 5 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    
    val m = lightness - c / 2
    val r = (rPrime + m).coerceIn(0f, 1f)
    val g = (gPrime + m).coerceIn(0f, 1f)
    val b = (bPrime + m).coerceIn(0f, 1f)
    
    return Color(red = r, green = g, blue = b)
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier, 
    isAbisal: Boolean,
    publicationDao: PublicationDao,
    onNavigate: (MobyScreen) -> Unit
) {
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    
    
    // Libros para el carrusel (los 5 más recientes en lectura)
    val followReading = remember(publications) {
        publications.filter { it.lastRead > 0 }
            .sortedByDescending { it.lastRead }
            .take(5)
    }

    // Libros para la lista de abajo (el resto)
    val historyBooks = remember(publications, followReading) {
        val followIds = followReading.map { it.id }.toSet()
        publications.filter { it.id !in followIds }
            .sortedByDescending { it.dateAdded }
            .take(10)
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { followReading.size })


    // Saludo según la hora
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Buenos días, viajero"
            in 12..18 -> "Buenas tardes, lector"
            else -> "Buenas noches, soñador"
        }
    }

    // Color de acento basado en el libro ACTUALMENTE visible en el carrusel
    val currentBook = if (followReading.isNotEmpty()) followReading[pagerState.currentPage] else null
    val accentColor = if (currentBook != null) generateColorFromBook(currentBook) else MaterialTheme.colorScheme.primary
    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 800),
        label = "gradientColor"
    )

    // Gradiente base según tema
    val bgStart = if (isAbisal) Color(0xFF011627) else Color(0xFFF8F9FA)
    val bgEnd = if (isAbisal) Color(0xFF0D1B2A) else Color(0xFFECF0F3)

    val dynamicGradient = Brush.verticalGradient(
        colors = listOf(
            bgStart,
            animatedAccentColor.copy(alpha = 0.15f),
            bgEnd
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dynamicGradient)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Poético y Amigable
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 56.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-1.5).sp
                    )
                }
            }


            // CARRUSEL ANIMADO (Seguir Leyendo)
            if (followReading.isNotEmpty()) {
                item {
                    Text(
                        text = "Continúa tu aventura",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
                
                item {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        pageSpacing = 16.dp
                    ) { page ->
                        val book = followReading[page]
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                        
                        val lerpScale = 0.85f + (1f - 0.85f) * (1f - absOffset)
                        val lerpAlpha = 0.5f + (1f - 0.5f) * (1f - absOffset)

                        com.example.moby.ui.components.FeaturedBookCard(
                            publication = book,
                            onContinueReading = { onNavigate(MobyScreen.Reader(book.id)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = lerpScale
                                    scaleY = lerpScale
                                    alpha = lerpAlpha
                                }
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(followReading.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .animateContentSize()
                            )
                            if (index < followReading.lastIndex) Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            } else if (publications.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(64.dp))
                    OutlinedButton(
                        onClick = { onNavigate(MobyScreen.Library) },
                        modifier = Modifier.fillMaxWidth(0.7f).height(64.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explorar la Biblioteca")
                    }
                }
            }

            // LISTA DE HISTORIAL
            if (historyBooks.isNotEmpty()) {
                item {
                    Text(
                        text = "Recientemente añadidos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 40.dp, bottom = 24.dp)
                    )
                }

                items(historyBooks) { book ->
                    com.example.moby.ui.components.PublicationListItem(
                        publication = book,
                        onClick = { onNavigate(MobyScreen.Reader(book.id)) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

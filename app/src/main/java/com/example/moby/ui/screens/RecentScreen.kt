package com.example.moby.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moby.MobyScreen
import com.example.moby.data.db.PublicationDao
import com.example.moby.models.Publication
import com.example.moby.ui.components.FeaturedBookCard
import com.example.moby.ui.components.PublicationListItem

@Composable
fun RecentScreen(
    publicationDao: PublicationDao,
    onNavigate: (MobyScreen) -> Unit
) {
    val publications by publicationDao.getAllPublications().collectAsState(initial = emptyList())
    
    val followReading = remember(publications) {
        publications.filter { it.lastRead > 0 }
            .sortedByDescending { it.lastRead }
            .take(5)
    }

    val pagerState = rememberPagerState(pageCount = { followReading.size })

    val recentlyAdded = remember(publications) {
        publications.sortedByDescending { it.dateAdded }
            .take(10)
    }

    if (publications.isEmpty()) {
        PlaceholderScreen("Tu Biblioteca", "Añade algunos libros para empezar a leer.")
    } else {
        val historyPubs = remember(publications, recentlyAdded, followReading) {
            val ignoreSet = if (followReading.isNotEmpty()) {
                followReading.map { it.id }.toSet()
            } else if (recentlyAdded.isNotEmpty()) {
                setOf(recentlyAdded.first().id)
            } else {
                emptySet()
            }
            recentlyAdded.filter { it.id !in ignoreSet }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {

                if (followReading.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) {
                                Text(
                                    text = "Seguir Leyendo",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-1).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp),
                                    contentPadding = PaddingValues(horizontal = 56.dp),
                                    pageSpacing = 16.dp
                                ) { page ->
                                    val publication = followReading[page]
                                    val pageOffset =
                                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

                                    FeaturedBookCard(
                                        publication = publication,
                                        onContinueReading = { onNavigate(MobyScreen.Reader(publication.id)) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                // Libro central: tamaño completo, elevado, opaco
                                                // Libros laterales: más pequeños, bajos y semitransparentes
                                                scaleX = lerp(0.78f, 1f, 1f - absOffset)
                                                scaleY = lerp(0.78f, 1f, 1f - absOffset)
                                                alpha = lerp(0.38f, 1f, 1f - absOffset)
                                                translationY = lerp(32f, 0f, 1f - absOffset)
                                            }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(followReading.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (pagerState.currentPage == index)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                )
                                        )
                                        if (index < followReading.lastIndex) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                } else if (recentlyAdded.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) {
                                Text(
                                    text = "last added",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-1).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                FeaturedBookCard(
                                    publication = recentlyAdded.first(),
                                    onContinueReading = { onNavigate(MobyScreen.Reader(recentlyAdded.first().id)) }
                                )
                            }
                        }
                }

                if (historyPubs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Historial",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 8.dp)
                        )
                    }

                    items(historyPubs) { publication ->
                        PublicationListItem(
                            publication = publication,
                            onClick = { onNavigate(MobyScreen.Reader(publication.id)) },
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}


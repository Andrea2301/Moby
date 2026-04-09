package com.example.moby.logic.readers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@Composable
fun CbzReaderComponent(
    filePath: String, 
    initialPage: Int, 
    onPageChanged: (Int) -> Unit,
    onTotalPagesReady: (Int) -> Unit,
    isVerticalMode: Boolean,
    onCenterTap: () -> Unit
) {
    var imageEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var fileLoadError by remember { mutableStateOf<Boolean>(false) }
    
    // Simple Memory Cache for bitmaps
    val bitmapCache = remember { LruCache<String, Bitmap>(20) } // Store last 20 entries

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val zip = ZipFile(file)
                    val entries = zip.entries().toList()
                        .filter { !it.isDirectory && isImageFile(it.name) }
                        .map { it.name }
                        .sorted() // Alphabetical sort is standard for CBZ
                    zip.close()
                    withContext(Dispatchers.Main) {
                        imageEntries = entries
                        onTotalPagesReady(entries.size)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { fileLoadError = true }
                e.printStackTrace()
            }
        }
    }

    if (fileLoadError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("Error al cargar el archivo CBZ.", color = Color.White)
        }
        return
    }

    if (imageEntries.isNotEmpty()) {
        var isPagingEnabled by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { imageEntries.size })
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

        LaunchedEffect(pagerState.currentPage) {
            if (!isVerticalMode) onPageChanged(pagerState.currentPage)
        }
        LaunchedEffect(listState.firstVisibleItemIndex) {
            if (isVerticalMode) onPageChanged(listState.firstVisibleItemIndex)
        }
        LaunchedEffect(initialPage) {
            if (!isVerticalMode && pagerState.currentPage != initialPage) {
                pagerState.scrollToPage(initialPage)
            } else if (isVerticalMode && listState.firstVisibleItemIndex != initialPage) {
                listState.scrollToItem(initialPage)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isVerticalMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) {
                                isPagingEnabled = true
                            }
                            
                            val change = event.changes.firstOrNull()
                            if (change != null && event.type == PointerEventType.Move) {
                                val delta = change.position - change.previousPosition
                                if (isPagingEnabled) {
                                    if (kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 2f) {
                                        isPagingEnabled = false
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(isVerticalMode) {
                    detectTapGestures { offset ->
                        if (isPagingEnabled) {
                            val layoutWidth = size.width
                            if (offset.x < layoutWidth * 0.2f) {
                                // Previous
                                scope.launch {
                                    if (isVerticalMode) {
                                        if (listState.firstVisibleItemIndex > 0) {
                                            listState.animateScrollToItem(listState.firstVisibleItemIndex - 1)
                                        }
                                    } else {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                }
                            } else if (offset.x > layoutWidth * 0.8f) {
                                // Next
                                scope.launch {
                                    if (isVerticalMode) {
                                        if (listState.firstVisibleItemIndex < imageEntries.size - 1) {
                                            listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
                                        }
                                    } else {
                                        if (pagerState.currentPage < imageEntries.size - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                }
                            } else {
                                onCenterTap()
                            }
                        }
                    }
                }
        ) {
            if (!isVerticalMode) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = isPagingEnabled,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.6f
                    ),
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    CbzPageRender(
                        filePath = filePath, 
                        entryName = imageEntries[page],
                        cache = bitmapCache,
                        onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed }
                    )
                }
            } else {
                val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

                LazyColumn(
                    state = listState,
                    flingBehavior = snapFlingBehavior,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(imageEntries.size) { page ->
                        CbzPageRender(
                            filePath = filePath, 
                            entryName = imageEntries[page],
                            cache = bitmapCache,
                            onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed }
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun CbzPageRender(
    filePath: String, 
    entryName: String, 
    cache: LruCache<String, Bitmap>,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(cache.get(entryName)) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(entryName) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onZoomChanged(false)
        
        if (cache.get(entryName) == null) {
            withContext(Dispatchers.IO) {
                try {
                    val zip = ZipFile(File(filePath))
                    val entry = zip.getEntry(entryName)
                    if (entry != null) {
                        val bmp = BitmapFactory.decodeStream(zip.getInputStream(entry))
                        cache.put(entryName, bmp)
                        withContext(Dispatchers.Main) {
                            bitmap = bmp
                        }
                    }
                    zip.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            bitmap = cache.get(entryName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val oldScale = scale
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    
                    if (scale != oldScale) {
                        onZoomChanged(scale > 1.05f)
                    }

                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                        onZoomChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = bitmap,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "CbzTransition"
        ) { targetBitmap ->
            if (targetBitmap != null) {
                Image(
                    bitmap = targetBitmap.asImageBitmap(),
                    contentDescription = entryName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Stable placeholder to prevent flickering
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f))
                )
            }
        }
    }
}

private fun isImageFile(name: String): Boolean {
    val low = name.lowercase()
    return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp")
}

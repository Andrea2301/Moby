package com.example.moby.logic.readers

import com.example.moby.ui.screens.ReaderTheme
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import java.io.File

@Composable
fun PdfReaderComponent(
    filePath: String, 
    initialPage: Int, 
    onPageChanged: (Int) -> Unit,
    onTotalPagesReady: (Int) -> Unit,
    isVerticalMode: Boolean,
    theme: ReaderTheme,
    onCenterTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    
    // Memory cache for PDF pages
    val bitmapCache = remember { LruCache<Int, Bitmap>(20) }

    var isPagingEnabled by remember { mutableStateOf(true) }

    DisposableEffect(filePath) {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                onTotalPagesReady(renderer.pageCount)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            pdfRenderer?.close()
        }
    }

    if (pageCount > 0 && pdfRenderer != null) {
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
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

        // Filtro de color según el tema activo
        val nightFilter = when (theme) {
            ReaderTheme.ABISAL -> ColorFilter.tint(Color.White, BlendMode.Difference)
            ReaderTheme.CRETA -> ColorFilter.tint(Color(0xFFF4ECD8), BlendMode.Multiply)
            ReaderTheme.PAPIRUS -> ColorFilter.tint(Color(0xFFD2D2D2), BlendMode.Multiply)
            else -> null
        }
        val bgColor = when (theme) {
            ReaderTheme.ARRECIFE -> Color(0xFFF8F9FA)
            ReaderTheme.CRETA -> Color(0xFFF4ECD8)
            ReaderTheme.PAPIRUS -> Color(0xFFD2D2D2)
            ReaderTheme.ABISAL -> Color(0xFF011627)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(isVerticalMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) isPagingEnabled = true
                            
                            val change = event.changes.firstOrNull()
                            if (change != null && event.type == PointerEventType.Move) {
                                val delta = change.position - change.previousPosition
                                if (isPagingEnabled) {
                                    if (kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 2f) isPagingEnabled = false
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
                                scope.launch {
                                    if (isVerticalMode) {
                                        if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(listState.firstVisibleItemIndex - 1)
                                    } else {
                                        if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            } else if (offset.x > layoutWidth * 0.8f) {
                                scope.launch {
                                    if (isVerticalMode) {
                                        if (listState.firstVisibleItemIndex < pageCount - 1) listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
                                    } else {
                                        if (pagerState.currentPage < pageCount - 1) pagerState.animateScrollToPage(pagerState.currentPage + 1)
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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PdfPageRender(pdfRenderer!!, page, bitmapCache, nightFilter)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageCount) { page ->
                        PdfPageRender(pdfRenderer!!, page, bitmapCache, nightFilter)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageRender(
    renderer: PdfRenderer, 
    pageIndex: Int, 
    cache: LruCache<Int, Bitmap>,
    colorFilter: ColorFilter?
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(cache.get(pageIndex)) }

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(pageIndex)
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    cache.put(pageIndex, b)
                    withContext(Dispatchers.Main) { bitmap = b }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    clip = true
                },
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter // 🎨 APPLY THE MAGIC HERE
            )
        } else {
            CircularProgressIndicator(color = Color.Gray)
        }
    }
}

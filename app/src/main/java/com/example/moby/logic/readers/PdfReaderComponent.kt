package com.example.moby.logic.readers

import com.example.moby.ui.screens.ReaderTheme
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.InputStream

@Composable
fun PdfReaderComponent(
    filePath: String,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTotalPagesReady: (Int) -> Unit,
    isVerticalMode: Boolean,
    theme: ReaderTheme,
    isSmartFitEnabled: Boolean,
    isTextReflowEnabled: Boolean,
    fontSize: Float,
    fontFamily: String,
    onCenterTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }

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

        val nightFilter = when (theme) {
            ReaderTheme.ABISAL -> {
                val matrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f, 0f
                ))
                ColorFilter.colorMatrix(matrix)
            }
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
        ) {
            if (!isVerticalMode) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = isPagingEnabled,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (isTextReflowEnabled) {
                        PdfReflowRender(
                            filePath = filePath,
                            pageIndex = page,
                            theme = theme,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            onCenterTap = onCenterTap
                        )
                    } else {
                        PdfPageRender(
                            renderer = pdfRenderer!!,
                            pageIndex = page,
                            cache = bitmapCache,
                            colorFilter = nightFilter,
                            isSmartFitEnabled = isSmartFitEnabled,
                            onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed },
                            onCenterTap = onCenterTap
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageCount) { page ->
                        if (isTextReflowEnabled) {
                            PdfReflowRender(
                                filePath = filePath,
                                pageIndex = page,
                                theme = theme,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                onCenterTap = onCenterTap
                            )
                        } else {
                            PdfPageRender(
                                renderer = pdfRenderer!!,
                                pageIndex = page,
                                cache = bitmapCache,
                                colorFilter = nightFilter,
                                isSmartFitEnabled = isSmartFitEnabled,
                                onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed },
                                onCenterTap = onCenterTap
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfReflowRender(
    filePath: String,
    pageIndex: Int,
    theme: ReaderTheme,
    fontSize: Float,
    fontFamily: String,
    onCenterTap: () -> Unit
) {
    var extractedText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, filePath) {
        withContext(Dispatchers.IO) {
            try {
                // Usamos .use para garantizar que los streams se cierren correctamente
                File(filePath).inputStream().use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        val stripper = PDFTextStripper()
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        val text = stripper.getText(document)

                        withContext(Dispatchers.Main) {
                            extractedText = text
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val textColor = when (theme) {
        ReaderTheme.ABISAL -> Color.White
        else -> Color.Black
    }

    val composeFontFamily = when (fontFamily) {
        "Serif" -> FontFamily.Serif
        "Sans" -> FontFamily.SansSerif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCenterTap() })
            }
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = textColor.copy(alpha = 0.5f))
        } else if (extractedText.isNullOrBlank()) {
            Text(
                "No se pudo extraer texto de esta página (posiblemente es una imagen).",
                color = textColor.copy(alpha = 0.6f),
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = extractedText!!,
                    color = textColor,
                    fontSize = (fontSize / 6).sp,
                    fontFamily = composeFontFamily,
                    lineHeight = ((fontSize / 6) * 1.5).sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun PdfPageRender(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: LruCache<Int, Bitmap>,
    colorFilter: ColorFilter?,
    isSmartFitEnabled: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onCenterTap: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(cache.get(pageIndex)) }
    var contentBounds by remember { mutableStateOf<RectF?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val smartFitScale = remember(bitmap, contentBounds, screenWidthPx) {
        if (bitmap != null && screenWidthPx > 0) {
            val referenceWidth = contentBounds?.width() ?: bitmap!!.width.toFloat()
            if (referenceWidth > 0) screenWidthPx / referenceWidth else 1f
        } else 1f
    }

    val baseScale = if (isSmartFitEnabled) smartFitScale else 1f

    val animatedScale by animateFloatAsState(
        targetValue = scale * baseScale,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "PdfScale"
    )

    val animatedOffset by animateOffsetAsState(
        targetValue = offset,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "PdfOffset"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "PdfAlpha"
    )

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(pageIndex)
                    val b = Bitmap.createBitmap((page.width * 2.5).toInt(), (page.height * 2.5).toInt(), Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    cache.put(pageIndex, b)
                    val bounds = detectContentBounds(b)
                    withContext(Dispatchers.Main) {
                        bitmap = b
                        contentBounds = bounds
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } else if (contentBounds == null) {
            launch(Dispatchers.Default) {
                val bounds = detectContentBounds(bitmap!!)
                withContext(Dispatchers.Main) { contentBounds = bounds }
            }
        }
    }

    LaunchedEffect(pageIndex) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onCenterTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            val targetScale = 2.5f
                            val screenCenter = Offset(screenWidthPx / 2f, (configuration.screenHeightDp.dp.toPx() / 2f))
                            val diff = (screenCenter - tapOffset)
                            val targetOffset = diff * (targetScale - 1f) / targetScale
                            val maxX = ((screenWidthPx * targetScale) - screenWidthPx).coerceAtLeast(0f) / (2f * targetScale)
                            val screenHeightPx = configuration.screenHeightDp.dp.toPx()
                            val maxY = ((screenHeightPx * targetScale) - screenHeightPx).coerceAtLeast(0f) / (2f * targetScale)
                            offset = Offset(targetOffset.x.coerceIn(-maxX, maxX), targetOffset.y.coerceIn(-maxY, maxY))
                            scale = targetScale
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()
                                if (zoomMotion > touchSlop || panMotion > touchSlop) pastTouchSlop = true
                            }
                            if (pastTouchSlop) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                val isZooming = zoomChange != 1f
                                if (scale > 1.05f || isZooming) {
                                    event.changes.forEach { it.consume() }
                                    scale = newScale
                                    if (scale > 1f) {
                                        val newOffset = offset + panChange
                                        val maxX = ((screenWidthPx * newScale) - screenWidthPx).coerceAtLeast(0f) / (2f * newScale)
                                        val screenHeightPx = configuration.screenHeightDp.dp.toPx()
                                        val maxY = ((screenHeightPx * newScale) - screenHeightPx).coerceAtLeast(0f) / (2f * newScale)
                                        offset = Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
                                    } else offset = Offset.Zero
                                    onZoomChanged(scale > 1.05f)
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val finalScale = animatedScale
            val smartCropOffset = remember(contentBounds, isSmartFitEnabled) {
                if (isSmartFitEnabled && contentBounds != null && bitmap != null) {
                    val centerPage = Offset(bitmap!!.width / 2f, bitmap!!.height / 2f)
                    val centerContent = Offset(contentBounds!!.centerX(), contentBounds!!.centerY())
                    (centerPage - centerContent) / finalScale
                } else Offset.Zero
            }

            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = finalScale
                        scaleY = finalScale
                        translationX = (animatedOffset.x + (if (isSmartFitEnabled) smartCropOffset.x else 0f)) * finalScale
                        translationY = animatedOffset.y * finalScale
                        alpha = animatedAlpha
                    },
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } else {
            CircularProgressIndicator(color = Color.Gray)
        }
    }
}

private fun detectContentBounds(bitmap: Bitmap): RectF {
    val width = bitmap.width
    val height = bitmap.height
    var left = width
    var top = height
    var right = 0
    var bottom = 0
    val step = 10
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r < 250 || g < 250 || b < 250) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < left || bottom < top) {
        RectF(0f, 0f, width.toFloat(), height.toFloat())
    } else {
        val margin = width * 0.04f
        RectF(
            (left - margin).coerceAtLeast(0f),
            (top - margin).coerceAtLeast(0f),
            (right + margin).coerceAtMost(width.toFloat()),
            (bottom + margin).coerceAtMost(height.toFloat())
        )
    }
}

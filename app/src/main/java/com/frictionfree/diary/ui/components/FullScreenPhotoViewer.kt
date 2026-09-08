package com.frictionfree.diary.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.launch
import java.io.File

/**
 * An immersive, full-screen photo lightbox with:
 * - Fluid horizontal swipe paging across all entry photos
 * - Non-interfering gesture detection (swiping works smoothly when 1x; zooming & panning active when zoomed)
 * - Next & Previous edge chevrons for 1-tap browsing
 * - Bottom indicator dots
 * - Double-tap to zoom (1x <-> 2.5x)
 * - Pinch-to-zoom (up to 5x) and pan
 * - Native Android photo sharing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPhotoViewer(
    photos: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val safeIndex = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeIndex) { photos.size }
    val coroutineScope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var currentScale by remember { mutableFloatStateOf(1f) }
    val context = LocalContext.current

    // Ensure we start on the selected photo
    LaunchedEffect(safeIndex) {
        if (pagerState.currentPage != safeIndex) {
            pagerState.scrollToPage(safeIndex)
        }
    }

    // Reset zoom level whenever page changes
    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
        ) {
            // Horizontal Pager: user can swipe between all attached photos
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentScale <= 1.05f
            ) { page ->
                val photoPath = photos[page]
                val isCurrentPage = pagerState.currentPage == page

                ZoomablePhotoItem(
                    path = photoPath,
                    isCurrentPage = isCurrentPage,
                    onScaleChanged = { scale ->
                        if (isCurrentPage) {
                            currentScale = scale
                        }
                    },
                    onSingleTap = {
                        controlsVisible = !controlsVisible
                    }
                )
            }

            // Left Navigation Button (Previous Photo)
            if (photos.size > 1 && pagerState.currentPage > 0 && controlsVisible) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous photo",
                        tint = Color.White
                    )
                }
            }

            // Right Navigation Button (Next Photo)
            if (photos.size > 1 && pagerState.currentPage < photos.size - 1 && controlsVisible) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next photo",
                        tint = Color.White
                    )
                }
            }

            // Bottom Page Indicator Dots (when multiple photos)
            if (photos.size > 1 && controlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    photos.forEachIndexed { dotIndex, _ ->
                        val isSelected = pagerState.currentPage == dotIndex
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.4f))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(dotIndex)
                                    }
                                }
                        )
                    }
                }
            }

            // Top overlay bar with close, photo counter, and share button
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(top = statusBarPadding, start = 12.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close viewer",
                            tint = Color.White
                        )
                    }

                    if (photos.size > 1) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} of ${photos.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val activePhoto = photos[pagerState.currentPage]
                            sharePhoto(context, activePhoto)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share photo",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhotoItem(
    path: String,
    isCurrentPage: Boolean,
    onScaleChanged: (Float) -> Unit,
    onSingleTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom and pan when navigating away from this page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onScaleChanged(1f)
        }
    }

    val model = remember(path) {
        if (path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || path.startsWith("file://")
        ) {
            path
        } else {
            File(path)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isCurrentPage) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                        onScaleChanged(scale)
                    },
                    onTap = {
                        onSingleTap()
                    }
                )
            }
            .pointerInput(isCurrentPage) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2) {
                            // Two or more fingers: PINCH TO ZOOM
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            onScaleChanged(newScale)

                            if (newScale > 1.05f) {
                                val maxPanX = 1200f * (newScale - 1f)
                                val maxPanY = 1600f * (newScale - 1f)
                                val newOffsetX = (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX)
                                val newOffsetY = (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                offset = Offset(newOffsetX, newOffsetY)
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && scale > 1.05f) {
                            // Single finger WHILE ZOOMED: PAN
                            val panChange = event.calculatePan()
                            val maxPanX = 1200f * (scale - 1f)
                            val maxPanY = 1600f * (scale - 1f)
                            val newOffsetX = (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX)
                            val newOffsetY = (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                            offset = Offset(newOffsetX, newOffsetY)
                            event.changes.forEach { it.consume() }
                        }
                        // When pointerCount == 1 and scale <= 1.05f:
                        // We DO NOT consume pointer movements so HorizontalPager can swipe freely!
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = "Full-size photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            val state = painter.state
            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    Text(
                        text = "Unable to load photo",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

private fun sharePhoto(context: Context, path: String) {
    try {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, path)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Photo Link"))
        } else {
            val file = File(path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            }
        }
    } catch (_: Exception) {}
}

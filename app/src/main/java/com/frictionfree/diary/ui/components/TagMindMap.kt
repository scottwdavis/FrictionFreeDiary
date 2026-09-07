package com.frictionfree.diary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frictionfree.diary.data.model.Tag
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class TagMapDisplayMode {
    CONSTELLATION,
    CLOUD
}

/**
 * A visually stunning Mind Map component for Hashtags.
 * Supports both a Constellation Network (neural web with glowing curved filaments)
 * and an organic Harmonic Cloud with dynamic bubble scaling.
 */
@Composable
fun TagMindMap(
    tags: List<Tag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    displayMode: TagMapDisplayMode = TagMapDisplayMode.CONSTELLATION,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    when (displayMode) {
        TagMapDisplayMode.CONSTELLATION -> {
            ConstellationMindMap(
                tags = tags,
                selectedTag = selectedTag,
                onTagSelected = onTagSelected,
                modifier = modifier
            )
        }
        TagMapDisplayMode.CLOUD -> {
            HarmonicCloudMindMap(
                tags = tags,
                selectedTag = selectedTag,
                onTagSelected = onTagSelected,
                modifier = modifier
            )
        }
    }
}

/**
 * Constellation Graph: renders tags as celestial nodes connected by glowing bezier filaments.
 * Users can pan around to explore all nodes smoothly.
 */
@Composable
private fun ConstellationMindMap(
    tags: List<Tag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val counts = tags.map { it.usageCount }
    val minCount = counts.minOrNull() ?: 1
    val maxCount = counts.maxOrNull() ?: 1

    // Sort tags: most prominent at center
    val sortedTags = remember(tags) { tags.sortedByDescending { it.usageCount } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panX += dragAmount.x
                    panY += dragAmount.y
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val center = Offset(widthPx / 2f + panX, heightPx / 2f + panY)

        // Calculate node positions based on golden angle spiral
        val nodePositions = remember(sortedTags, center) {
            val positions = mutableListOf<Offset>()
            val goldenAngle = 2.3999632f // 137.5 degrees in radians
            val baseRadius = with(density) { 62.dp.toPx() }
            val radiusStep = with(density) { 34.dp.toPx() }

            sortedTags.forEachIndexed { index, _ ->
                if (index == 0) {
                    positions.add(center)
                } else {
                    val angle = index * goldenAngle
                    val radius = baseRadius + sqrt(index.toFloat()) * radiusStep
                    val x = center.x + radius * cos(angle)
                    val y = center.y + radius * sin(angle)
                    positions.add(Offset(x, y))
                }
            }
            positions
        }

        // Connecting filaments between nearest neighbors
        val connections = remember(sortedTags) {
            val edges = mutableListOf<Pair<Int, Int>>()
            for (i in 1 until sortedTags.size) {
                // Connect to center if small set, or parent in spiral
                val target = if (i <= 4) 0 else ((i - 1) / 2)
                edges.add(Pair(target, i))
                // Add an occasional neighbor cross-link
                if (i > 2 && i % 3 == 0) {
                    edges.add(Pair(i - 1, i))
                }
            }
            edges
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant

        // 1. Draw glowing connecting filament lines & ambient glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw filaments
            connections.forEach { (fromIdx, toIdx) ->
                if (fromIdx < nodePositions.size && toIdx < nodePositions.size) {
                    val p1 = nodePositions[fromIdx]
                    val p2 = nodePositions[toIdx]

                    val isHighlighted = sortedTags[fromIdx].name.equals(selectedTag, ignoreCase = true) ||
                            sortedTags[toIdx].name.equals(selectedTag, ignoreCase = true)

                    val strokeColor = if (isHighlighted) {
                        primaryColor.copy(alpha = 0.85f)
                    } else {
                        outlineColor.copy(alpha = 0.30f)
                    }
                    val strokeWidth = if (isHighlighted) 2.5.dp.toPx() else 1.2.dp.toPx()

                    // Gentle curved Bezier filament
                    val midX = (p1.x + p2.x) / 2f
                    val midY = (p1.y + p2.y) / 2f
                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val ctrlPoint = Offset(midX - dy * 0.12f, midY + dx * 0.12f)

                    val path = Path().apply {
                        moveTo(p1.x, p1.y)
                        quadraticTo(ctrlPoint.x, ctrlPoint.y, p2.x, p2.y)
                    }

                    if (isHighlighted) {
                        // Ambient glow beneath active filament
                        drawPath(
                            path = path,
                            color = primaryColor.copy(alpha = 0.25f),
                            style = Stroke(width = strokeWidth * 2.8f, cap = StrokeCap.Round)
                        )
                    }

                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Draw ambient radial glow beneath nodes
            sortedTags.forEachIndexed { index, tag ->
                if (index < nodePositions.size) {
                    val pos = nodePositions[index]
                    val isSelected = tag.name.equals(selectedTag, ignoreCase = true)
                    val glowRadius = if (isSelected) 54.dp.toPx() else 34.dp.toPx()
                    val glowColor = if (isSelected) primaryColor.copy(alpha = 0.35f) else primaryColor.copy(alpha = 0.08f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                            center = pos,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = pos
                    )
                }
            }
        }

        // 2. Render Interactive Constellation Nodes
        sortedTags.forEachIndexed { index, tag ->
            if (index < nodePositions.size) {
                val pos = nodePositions[index]
                val isSelected = tag.name.equals(selectedTag, ignoreCase = true)
                val weight = if (maxCount > minCount) {
                    ((tag.usageCount - minCount).toFloat() / (maxCount - minCount)).coerceIn(0f, 1f)
                } else {
                    0.5f
                }

                ConstellationNode(
                    tag = tag,
                    weight = weight,
                    isSelected = isSelected,
                    centerOffset = pos,
                    onClick = {
                        if (isSelected) onTagSelected(null)
                        else onTagSelected(tag.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConstellationNode(
    tag: Tag,
    weight: Float,
    isSelected: Boolean,
    centerOffset: Offset,
    onClick: () -> Unit
) {
    val hash = abs(tag.name.hashCode())
    val nodeTint = getNodeTint(hash, weight)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "nodeScale"
    )

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .offset {
                // Approximate centering based on typical node size
                IntOffset(
                    (centerOffset.x - 48.dp.toPx()).roundToInt(),
                    (centerOffset.y - 18.dp.toPx()).roundToInt()
                )
            }
            .scale(scale)
            .shadow(
                elevation = if (isSelected) 8.dp else 2.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        )
                    } else {
                        listOf(
                            nodeTint,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    }
                )
            )
            .border(borderStroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = (10f + weight * 6f).dp, vertical = (5f + weight * 4f).dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "#${tag.name}",
                fontSize = (12.5f + weight * 4f).sp,
                fontWeight = if (isSelected || weight > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Pill Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${tag.usageCount}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Harmonic Cloud: Staggered organic bubbles with vibrant subtle gradients.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HarmonicCloudMindMap(
    tags: List<Tag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = tags.map { it.usageCount }
    val minCount = counts.minOrNull() ?: 1
    val maxCount = counts.maxOrNull() ?: 1

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        tags.forEach { tag ->
            val isSelected = tag.name.equals(selectedTag, ignoreCase = true)
            val weight = if (maxCount > minCount) {
                ((tag.usageCount - minCount).toFloat() / (maxCount - minCount)).coerceIn(0f, 1f)
            } else {
                0.5f
            }

            HarmonicCloudBubble(
                tag = tag,
                weight = weight,
                isSelected = isSelected,
                onClick = {
                    if (isSelected) onTagSelected(null)
                    else onTagSelected(tag.name)
                }
            )
        }
    }
}

@Composable
private fun HarmonicCloudBubble(
    tag: Tag,
    weight: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hash = abs(tag.name.hashCode())
    val staggerOffsets = listOf(2.dp, 14.dp, 0.dp, 10.dp, 6.dp, 16.dp, 4.dp, 12.dp)
    val topStagger = staggerOffsets[hash % staggerOffsets.size]

    val fontSize = (13f + weight * 7f).sp
    val horizontalPadding = (12f + weight * 8f).dp
    val verticalPadding = (6f + weight * 5f).dp

    val bubbleShape = RoundedCornerShape(
        topStart = (18f + weight * 6f).dp,
        topEnd = (14f + (hash % 5) + weight * 4f).dp,
        bottomEnd = (18f + (hash % 4) + weight * 4f).dp,
        bottomStart = (15f + (hash % 6) + weight * 5f).dp
    )

    val nodeTint = getNodeTint(hash, weight)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cloudScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else nodeTint,
        label = "cloudColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "cloudContentColor"
    )

    Box(
        modifier = Modifier
            .padding(top = topStagger)
            .scale(scale)
            .shadow(elevation = if (isSelected) 5.dp else 1.5.dp, shape = bubbleShape, clip = false)
            .clip(bubbleShape)
            .background(containerColor)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                bubbleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "#${tag.name}",
                fontSize = fontSize,
                fontWeight = if (weight > 0.6f) FontWeight.Bold else if (weight > 0.3f) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else contentColor.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${tag.usageCount}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun getNodeTint(hash: Int, weight: Float): Color {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val colorPalettes = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.09f + weight * 0.09f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f + weight * 0.09f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f + weight * 0.09f),
        Color(0xFF00897B).copy(alpha = 0.09f + weight * 0.09f), // Teal
        Color(0xFFEF6C00).copy(alpha = 0.08f + weight * 0.08f), // Amber
        Color(0xFF7B1FA2).copy(alpha = 0.09f + weight * 0.09f), // Purple
        Color(0xFF1E88E5).copy(alpha = 0.09f + weight * 0.09f), // Indigo/Blue
        Color(0xFF43A047).copy(alpha = 0.09f + weight * 0.09f)  // Emerald
    )
    val tint = colorPalettes[hash % colorPalettes.size]
    val alpha = tint.alpha
    val r = (tint.red * alpha + surface.red * (1 - alpha)).coerceIn(0f, 1f)
    val g = (tint.green * alpha + surface.green * (1 - alpha)).coerceIn(0f, 1f)
    val b = (tint.blue * alpha + surface.blue * (1 - alpha)).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
}

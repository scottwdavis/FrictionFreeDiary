package com.frictionfree.diary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frictionfree.diary.data.model.Tag
import kotlin.math.abs

/**
 * A mind-map style tag cloud where frequently used hashtags are proportionately
 * larger, have organic asymmetric pill contours, and feature staggered vertical
 * offsets to avoid rigid grid-like alignment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagMindMap(
    tags: List<Tag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

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

            MindMapTagBubble(
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
private fun MindMapTagBubble(
    tag: Tag,
    weight: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hash = abs(tag.name.hashCode())

    // 1. Organic Staggering: deterministic vertical offset per tag so rows are pleasantly unaligned
    val staggerOffsets = listOf(2.dp, 16.dp, 0.dp, 12.dp, 6.dp, 18.dp, 4.dp, 14.dp, 8.dp)
    val topStagger = staggerOffsets[hash % staggerOffsets.size]

    // 2. Proportional Dimensions
    val fontSize = (13f + weight * 8f).sp
    val badgeFontSize = (10f + weight * 3f).sp
    val horizontalPadding = (12f + weight * 10f).dp
    val verticalPadding = (6f + weight * 6f).dp

    // 3. Organic Bubble Shape: slightly asymmetric rounded corners
    val c1 = (16f + weight * 8f).dp
    val c2 = (14f + (hash % 5) + weight * 6f).dp
    val c3 = (18f + (hash % 4) + weight * 6f).dp
    val c4 = (15f + (hash % 6) + weight * 7f).dp
    val bubbleShape = RoundedCornerShape(
        topStart = c1,
        topEnd = c2,
        bottomEnd = c3,
        bottomStart = c4
    )

    // 4. Subtle Harmonious Node Palettes based on Tag Hash
    val nodeTint = getNodeTint(hash, weight)

    // 5. Animations
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tagScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            nodeTint
        },
        label = "tagContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "tagContentColor"
    )

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val elevation = if (isSelected) (4.dp + (weight * 4).dp) else (1.dp + (weight * 2).dp)

    Box(
        modifier = Modifier
            .padding(top = topStagger)
            .scale(scale)
            .shadow(elevation = elevation, shape = bubbleShape, clip = false)
            .clip(bubbleShape)
            .background(containerColor)
            .border(borderStroke, bubbleShape)
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
                fontWeight = if (weight > 0.65f) FontWeight.Bold else if (weight > 0.3f) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Count badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            contentColor.copy(alpha = 0.10f)
                        }
                    )
                    .padding(horizontal = (6f + weight * 2f).dp, vertical = (1.5f + weight * 1.5f).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${tag.usageCount}",
                    fontSize = badgeFontSize,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Generates an organic, subtle pastel/tinted color for the node based on hash and weight.
 */
@Composable
private fun getNodeTint(hash: Int, weight: Float): Color {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val colorSchemes = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + weight * 0.08f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f + weight * 0.08f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f + weight * 0.08f),
        Color(0xFF00796B).copy(alpha = 0.08f + weight * 0.08f), // Teal
        Color(0xFFE65100).copy(alpha = 0.07f + weight * 0.07f), // Amber / Orange
        Color(0xFF6A1B9A).copy(alpha = 0.08f + weight * 0.08f), // Purple
        Color(0xFF1565C0).copy(alpha = 0.08f + weight * 0.08f), // Blue
        Color(0xFF2E7D32).copy(alpha = 0.08f + weight * 0.08f)  // Green
    )
    val baseTint = colorSchemes[hash % colorSchemes.size]

    // Blend tint over base surface
    return surface.compositeOverTint(baseTint)
}

private fun Color.compositeOverTint(tint: Color): Color {
    val alpha = tint.alpha
    val r = (tint.red * alpha + this.red * (1 - alpha)).coerceIn(0f, 1f)
    val g = (tint.green * alpha + this.green * (1 - alpha)).coerceIn(0f, 1f)
    val b = (tint.blue * alpha + this.blue * (1 - alpha)).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
}

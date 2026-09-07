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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frictionfree.diary.data.model.Tag
import kotlin.math.abs

/**
 * A fluid, organic Mind Map where hashtags are non-overlapping,
 * more frequently used hashtags are noticeably larger, and staggered
 * vertical offsets give a natural, non-grid clustering without connecting lines.
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

    // Sort tags: prominent tags distributed nicely throughout the flow
    val sortedTags = tags.sortedWith(
        compareByDescending<Tag> { it.usageCount }.thenBy { it.name }
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        sortedTags.forEach { tag ->
            val isSelected = tag.name.equals(selectedTag, ignoreCase = true)
            val weight = if (maxCount > minCount) {
                ((tag.usageCount - minCount).toFloat() / (maxCount - minCount)).coerceIn(0f, 1f)
            } else {
                0.5f
            }

            OrganicTagBubble(
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
private fun OrganicTagBubble(
    tag: Tag,
    weight: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hash = abs(tag.name.hashCode())

    // 1. Organic Staggering: deterministic top margin offset so items do not line up in a rigid horizontal row
    val staggerOffsets = listOf(0.dp, 12.dp, 4.dp, 16.dp, 2.dp, 10.dp, 6.dp, 14.dp)
    val topStagger = staggerOffsets[hash % staggerOffsets.size]

    // 2. Proportional Dimensions based on usage weight
    // Infrequently used tags start at 13.5sp, high-usage tags scale up to 21sp
    val fontSize = (13.5f + weight * 7.5f).sp
    val badgeFontSize = (10f + weight * 3f).sp
    val horizontalPadding = (12f + weight * 12f).dp
    val verticalPadding = (7f + weight * 7f).dp

    // 3. Organic Bubble Shape: gently asymmetrical rounded corners (resembling river stones)
    val c1 = (18f + weight * 8f).dp
    val c2 = (14f + (hash % 6) + weight * 6f).dp
    val c3 = (19f + (hash % 5) + weight * 6f).dp
    val c4 = (15f + (hash % 7) + weight * 7f).dp
    val bubbleShape = RoundedCornerShape(
        topStart = c1,
        topEnd = c2,
        bottomEnd = c3,
        bottomStart = c4
    )

    val nodeTint = getNodeTint(hash, weight)

    // 4. Spring-based animation
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.10f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tagScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else nodeTint,
        label = "tagContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "tagContentColor"
    )

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    val elevation = if (isSelected) (5.dp + (weight * 4).dp) else (1.dp + (weight * 2).dp)

    Box(
        modifier = Modifier
            .padding(top = topStagger)
            .scale(scale)
            .shadow(elevation = elevation, shape = bubbleShape, clip = false)
            .clip(bubbleShape)
            .background(
                Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        )
                    } else {
                        listOf(
                            containerColor,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    }
                )
            )
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
                fontWeight = if (isSelected || weight > 0.6f) FontWeight.Bold else if (weight > 0.3f) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )

            Spacer(modifier = Modifier.width((5f + weight * 2f).dp))

            // Count Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else contentColor.copy(alpha = 0.10f)
                    )
                    .padding(horizontal = (6f + weight * 2f).dp, vertical = (2f + weight * 1.5f).dp),
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
 * Harmonious, soft tint based on tag hash and weight.
 */
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


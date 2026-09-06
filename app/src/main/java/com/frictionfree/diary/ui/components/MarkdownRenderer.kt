package com.frictionfree.diary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(
    markdownText: String,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null
) {
    if (markdownText.isBlank()) return

    Column(modifier = modifier.fillMaxWidth()) {
        val lines = markdownText.lines()
        var inCodeBlock = false
        val codeBlockBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trimEnd()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    CodeBlock(code = codeBlockBuffer.toString())
                    codeBlockBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockBuffer.append(line).append("\n")
                continue
            }

            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    QuoteBlock(text = trimmed.removePrefix("> "))
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                    TaskItem(text = trimmed.substring(6), isChecked = false)
                }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("* [x] ") ||
                trimmed.startsWith("- [X] ") || trimmed.startsWith("* [X] ") -> {
                    TaskItem(text = trimmed.substring(6), isChecked = true)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    BulletItem(text = trimmed.substring(2), onTagClick = onTagClick)
                }
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    MarkdownInlineText(
                        text = line,
                        onTagClick = onTagClick
                    )
                }
            }
        }

        if (inCodeBlock && codeBlockBuffer.isNotEmpty()) {
            CodeBlock(code = codeBlockBuffer.toString())
        }
    }
}

@Composable
fun MarkdownInlineText(
    text: String,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null
) {
    val annotatedString = buildAnnotatedString {
        // Regex for bold, italic, strikethrough, hashtags, inline code
        val tagColor = MaterialTheme.colorScheme.primary
        val parts = text.split(" ")

        for ((index, part) in parts.withIndex()) {
            if (part.startsWith("#") && part.length > 1 && !part.startsWith("# ")) {
                val cleanTag = part.removePrefix("#").trimEnd(',', '.', '!', '?', ';', ':')
                pushStringAnnotation(tag = "TAG", annotation = cleanTag)
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)) {
                    append(part)
                }
                pop()
            } else if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part.removeSurrounding("**"))
                }
            } else if (part.startsWith("*") && part.endsWith("*") && part.length > 2) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(part.removeSurrounding("*"))
                }
            } else if (part.startsWith("~~") && part.endsWith("~~") && part.length > 4) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(part.removeSurrounding("~~"))
                }
            } else if (part.startsWith("`") && part.endsWith("`") && part.length > 2) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    append(part.removeSurrounding("`"))
                }
            } else {
                append(part)
            }

            if (index < parts.size - 1) {
                append(" ")
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun BulletItem(text: String, onTagClick: ((String) -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        MarkdownInlineText(text = text, onTagClick = onTagClick)
    }
}

@Composable
private fun TaskItem(text: String, isChecked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = if (isChecked) "Completed" else "Incomplete",
            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuoteBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = code.trimEnd(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

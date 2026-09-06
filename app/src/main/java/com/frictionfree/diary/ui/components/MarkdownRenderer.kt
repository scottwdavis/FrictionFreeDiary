package com.frictionfree.diary.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun MarkdownRenderer(
    markdownText: String,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null
) {
    if (markdownText.isBlank()) return

    // Preprocess: normalize multiline footnote links like [label\n\n](<url>) -> [label](<url>)
    val normalizedText = remember(markdownText) {
        markdownText.replace(Regex("""\[([\s\S]*?)\]\(((?:<[^>]+>)|(?:[^\s)]+))\)""")) { match ->
            val label = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            "[$label]($url)"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val lines = normalizedText.lines()
        var inCodeBlock = false
        val codeBlockBuffer = StringBuilder()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimEnd()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(code = codeBlockBuffer.toString())
                    codeBlockBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBlockBuffer.append(line).append("\n")
                i++
                continue
            }

            // Standalone image line: ![alt](url) or ![alt](<url>)
            val standaloneImageMatch = Regex("""^!\[([^\]]*)\]\(((?:<[^>]+>)|(?:[^\s)]+))\)$""").matchEntire(trimmed.trim())
            if (standaloneImageMatch != null) {
                val alt = standaloneImageMatch.groupValues[1]
                val rawUrl = standaloneImageMatch.groupValues[2].trim().removeSurrounding("<", ">")
                MarkdownImageBlock(url = rawUrl, alt = alt)
                i++
                continue
            }

            // Multi-line blockquote grouping: collects all consecutive > lines
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size) {
                    val currentTrimmed = lines[i].trimEnd()
                    if (currentTrimmed.startsWith(">")) {
                        val content = currentTrimmed.removePrefix(">").let {
                            if (it.startsWith(" ")) it.substring(1) else it
                        }
                        quoteLines.add(content)
                        i++
                    } else {
                        break
                    }
                }
                QuoteBlock(lines = quoteLines, onTagClick = onTagClick)
                continue
            }

            when {
                trimmed.startsWith("#### ") -> {
                    MarkdownInlineText(
                        text = trimmed.removePrefix("#### "),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    MarkdownInlineText(
                        text = trimmed.removePrefix("### "),
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    MarkdownInlineText(
                        text = trimmed.removePrefix("## "),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    MarkdownInlineText(
                        text = trimmed.removePrefix("# "),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                    TaskItem(text = trimmed.substring(6), isChecked = false, onTagClick = onTagClick)
                }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("* [x] ") ||
                trimmed.startsWith("- [X] ") || trimmed.startsWith("* [X] ") -> {
                    TaskItem(text = trimmed.substring(6), isChecked = true, onTagClick = onTagClick)
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
            i++
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
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onTagClick: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val onUrlClick: (String) -> Unit = remember(uriHandler, context) {
        { rawTarget ->
            val clean = rawTarget.trim().removeSurrounding("<", ">").replace("\\_", "_")
            val url = if (clean.startsWith("www.", ignoreCase = true)) "https://$clean" else clean
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val annotatedString = remember(text, primaryColor, surfaceVariant) {
        buildMarkdownAnnotatedString(
            text = text,
            linkColor = primaryColor,
            tagColor = primaryColor,
            codeBackgroundColor = surfaceVariant,
            onTagClick = onTagClick,
            onUrlClick = onUrlClick
        )
    }

    Text(
        text = annotatedString,
        style = textStyle,
        color = color,
        modifier = modifier.padding(vertical = 2.dp)
    )
}

fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    tagColor: Color,
    codeBackgroundColor: Color,
    onTagClick: ((String) -> Unit)?,
    onUrlClick: (String) -> Unit
): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownSpans(
            text = text,
            depth = 0,
            isInsideLink = false,
            linkColor = linkColor,
            tagColor = tagColor,
            codeBackgroundColor = codeBackgroundColor,
            onTagClick = onTagClick,
            onUrlClick = onUrlClick
        )
    }
}

private data class TokenCandidate(
    val start: Int,
    val end: Int,
    val type: MarkdownTokenType,
    val matchResult: MatchResult
)

private enum class MarkdownTokenType {
    INLINE_IMAGE,
    MARKDOWN_LINK,
    RAW_URL,
    BOLD_ITALIC,
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    INLINE_CODE,
    HASHTAG
}

private val inlineImageRegex = Regex("""!\[([^\]]*)\]\(((?:<[^>]+>)|(?:[^\s)]+))\)""")
private val markdownLinkRegex = Regex("""\[([^\]]+)\]\(((?:<[^>]+>)|(?:[^\s)]+))\)""")
private val rawUrlRegex = Regex("""(?<![\(\[<])(https?://[^\s<>"'{}|\\^`]+|www\.[^\s<>"'{}|\\^`]+)""", RegexOption.IGNORE_CASE)
private val boldItalicRegex = Regex("""\*\*\*([^*]+)\*\*\*|___([^_]+)___""")
private val boldRegex = Regex("""\*\*([^*]+)\*\*|__([^_]+)__""")
private val italicRegex = Regex("""(?<!\w)\*([^*]+)\*(?!\w)|(?<!\w)_([^_]+)_(?!\w)""")
private val strikethroughRegex = Regex("""~~([^~]+)~~""")
private val inlineCodeRegex = Regex("""`([^`]+)`""")
private val hashtagRegex = Regex("""(?<=^|\s)#([a-zA-Z0-9_\u0080-\uFFFF]+)""")

private fun AnnotatedString.Builder.appendMarkdownSpans(
    text: String,
    depth: Int,
    isInsideLink: Boolean,
    linkColor: Color,
    tagColor: Color,
    codeBackgroundColor: Color,
    onTagClick: ((String) -> Unit)?,
    onUrlClick: (String) -> Unit
) {
    if (text.isEmpty()) return
    if (depth > 5) {
        append(text)
        return
    }

    var cursor = 0
    val length = text.length

    while (cursor < length) {
        var earliest: TokenCandidate? = null

        fun checkPattern(regex: Regex, type: MarkdownTokenType) {
            val match = regex.find(text, cursor) ?: return
            val start = match.range.first
            if (earliest == null || start < earliest!!.start) {
                earliest = TokenCandidate(
                    start = start,
                    end = match.range.last + 1,
                    type = type,
                    matchResult = match
                )
            }
        }

        if (!isInsideLink) {
            checkPattern(inlineImageRegex, MarkdownTokenType.INLINE_IMAGE)
            checkPattern(markdownLinkRegex, MarkdownTokenType.MARKDOWN_LINK)
            checkPattern(rawUrlRegex, MarkdownTokenType.RAW_URL)
        }
        checkPattern(boldItalicRegex, MarkdownTokenType.BOLD_ITALIC)
        checkPattern(boldRegex, MarkdownTokenType.BOLD)
        checkPattern(italicRegex, MarkdownTokenType.ITALIC)
        checkPattern(strikethroughRegex, MarkdownTokenType.STRIKETHROUGH)
        checkPattern(inlineCodeRegex, MarkdownTokenType.INLINE_CODE)
        checkPattern(hashtagRegex, MarkdownTokenType.HASHTAG)

        if (earliest == null) {
            append(text.substring(cursor))
            break
        }

        val token = earliest!!
        if (token.start > cursor) {
            append(text.substring(cursor, token.start))
        }

        when (token.type) {
            MarkdownTokenType.MARKDOWN_LINK -> {
                val label = token.matchResult.groupValues[1]
                val rawUrl = token.matchResult.groupValues[2].trim().removeSurrounding("<", ">").replace("\\_", "_")
                val cleanUrl = if (rawUrl.startsWith("www.", ignoreCase = true)) "https://$rawUrl" else rawUrl

                var displayLabel = label.replace(Regex("""<[^>]+>"""), "").trim()
                if (displayLabel.isBlank() || displayLabel.equals("null", ignoreCase = true)) {
                    displayLabel = if (cleanUrl.contains(Regex("""\.(jpe?g|png|webp|gif)""", RegexOption.IGNORE_CASE))) "ðŸ“· Image" else "ðŸ”— Link"
                }

                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    ),
                    focusedStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    ),
                    hoveredStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    ),
                    pressedStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                )

                withLink(
                    LinkAnnotation.Url(
                        url = cleanUrl,
                        styles = linkStyles,
                        linkInteractionListener = { onUrlClick(cleanUrl) }
                    )
                ) {
                    appendMarkdownSpans(
                        text = displayLabel,
                        depth = depth + 1,
                        isInsideLink = true,
                        linkColor = linkColor,
                        tagColor = tagColor,
                        codeBackgroundColor = codeBackgroundColor,
                        onTagClick = onTagClick,
                        onUrlClick = onUrlClick
                    )
                }
            }

            MarkdownTokenType.INLINE_IMAGE -> {
                val alt = token.matchResult.groupValues[1]
                val rawUrl = token.matchResult.groupValues[2].trim().removeSurrounding("<", ">").replace("\\_", "_")
                val cleanUrl = if (rawUrl.startsWith("www.", ignoreCase = true)) "https://$rawUrl" else rawUrl
                val displayLabel = if (alt.isBlank() || alt.equals("null", ignoreCase = true)) "ðŸ“· Image" else "ðŸ“· $alt"

                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                )

                withLink(
                    LinkAnnotation.Url(
                        url = cleanUrl,
                        styles = linkStyles,
                        linkInteractionListener = { onUrlClick(cleanUrl) }
                    )
                ) {
                    append(displayLabel)
                }
            }

            MarkdownTokenType.RAW_URL -> {
                val fullMatch = token.matchResult.value
                var urlText = fullMatch
                var trailingPunct = ""
                while (urlText.isNotEmpty() && urlText.last() in listOf('.', ',', '!', '?', ')', ';', ':')) {
                    trailingPunct = urlText.last() + trailingPunct
                    urlText = urlText.dropLast(1)
                }

                val cleanUrl = if (urlText.startsWith("www.", ignoreCase = true)) "https://$urlText" else urlText
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                )

                withLink(
                    LinkAnnotation.Url(
                        url = cleanUrl,
                        styles = linkStyles,
                        linkInteractionListener = { onUrlClick(cleanUrl) }
                    )
                ) {
                    append(urlText)
                }
                if (trailingPunct.isNotEmpty()) {
                    append(trailingPunct)
                }
            }

            MarkdownTokenType.HASHTAG -> {
                val tag = token.matchResult.groupValues[1]
                val tagStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = tagColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                if (onTagClick != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = tag,
                            styles = tagStyles,
                            linkInteractionListener = { onTagClick(tag) }
                        )
                    ) {
                        append("#$tag")
                    }
                } else {
                    withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)) {
                        append("#$tag")
                    }
                }
            }

            MarkdownTokenType.BOLD_ITALIC -> {
                val inner = token.matchResult.groupValues[1].ifEmpty { token.matchResult.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    appendMarkdownSpans(
                        text = inner,
                        depth = depth + 1,
                        isInsideLink = isInsideLink,
                        linkColor = linkColor,
                        tagColor = tagColor,
                        codeBackgroundColor = codeBackgroundColor,
                        onTagClick = onTagClick,
                        onUrlClick = onUrlClick
                    )
                }
            }

            MarkdownTokenType.BOLD -> {
                val inner = token.matchResult.groupValues[1].ifEmpty { token.matchResult.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdownSpans(
                        text = inner,
                        depth = depth + 1,
                        isInsideLink = isInsideLink,
                        linkColor = linkColor,
                        tagColor = tagColor,
                        codeBackgroundColor = codeBackgroundColor,
                        onTagClick = onTagClick,
                        onUrlClick = onUrlClick
                    )
                }
            }

            MarkdownTokenType.ITALIC -> {
                val inner = token.matchResult.groupValues[1].ifEmpty { token.matchResult.groupValues[2] }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendMarkdownSpans(
                        text = inner,
                        depth = depth + 1,
                        isInsideLink = isInsideLink,
                        linkColor = linkColor,
                        tagColor = tagColor,
                        codeBackgroundColor = codeBackgroundColor,
                        onTagClick = onTagClick,
                        onUrlClick = onUrlClick
                    )
                }
            }

            MarkdownTokenType.STRIKETHROUGH -> {
                val inner = token.matchResult.groupValues[1]
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendMarkdownSpans(
                        text = inner,
                        depth = depth + 1,
                        isInsideLink = isInsideLink,
                        linkColor = linkColor,
                        tagColor = tagColor,
                        codeBackgroundColor = codeBackgroundColor,
                        onTagClick = onTagClick,
                        onUrlClick = onUrlClick
                    )
                }
            }

            MarkdownTokenType.INLINE_CODE -> {
                val inner = token.matchResult.groupValues[1]
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor)) {
                    append(inner)
                }
            }
        }

        cursor = token.end
    }
}

@Composable
private fun MarkdownImageBlock(url: String, alt: String) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                try {
                    uriHandler.openUri(url)
                } catch (_: Exception) {}
            }
    ) {
        AsyncImage(
            model = if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://")) url else File(url),
            contentDescription = alt.ifBlank { "Journal image" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            contentScale = ContentScale.Crop
        )
    }
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
            text = "\u2022",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        MarkdownInlineText(
            text = text,
            onTagClick = onTagClick
        )
    }
}

@Composable
private fun TaskItem(text: String, isChecked: Boolean, onTagClick: ((String) -> Unit)?) {
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
        MarkdownInlineText(
            text = text,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            onTagClick = onTagClick
        )
    }
}

@Composable
private fun QuoteBlock(lines: List<String>, onTagClick: ((String) -> Unit)?) {
    val barColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, bottomStart = 2.dp))
            .background(surfaceColor)
            .drawBehind {
                val barWidth = 4.dp.toPx()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (line in lines) {
                if (line.isBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    MarkdownInlineText(
                        text = line,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onTagClick = onTagClick
                    )
                }
            }
        }
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
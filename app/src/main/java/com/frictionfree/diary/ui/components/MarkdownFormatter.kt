package com.frictionfree.diary.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MarkdownAction {
    BOLD,
    ITALIC,
    HEADING,
    BULLET_LIST,
    TASK_CHECKBOX,
    QUOTE,
    CODE,
    HASHTAG,
    TIMESTAMP
}

object MarkdownFormatter {

    fun applyAction(
        value: TextFieldValue,
        action: MarkdownAction,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): TextFieldValue {
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(0, text.length)
        val hasSelection = start < end
        val selectedText = if (hasSelection) text.substring(start, end) else ""

        return when (action) {
            MarkdownAction.BOLD -> {
                if (hasSelection) {
                    if (selectedText.startsWith("**") && selectedText.endsWith("**") && selectedText.length >= 4) {
                        // Toggle OFF
                        val unwrapped = selectedText.substring(2, selectedText.length - 2)
                        val newText = text.replaceRange(start, end, unwrapped)
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(start, start + unwrapped.length)
                        )
                    } else {
                        // Wrap
                        val wrapped = "**$selectedText**"
                        val newText = text.replaceRange(start, end, wrapped)
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(start + 2, start + 2 + selectedText.length)
                        )
                    }
                } else {
                    val insert = "****"
                    val newText = text.replaceRange(start, end, insert)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 2)
                    )
                }
            }

            MarkdownAction.ITALIC -> {
                if (hasSelection) {
                    if (selectedText.startsWith("*") && selectedText.endsWith("*") && !selectedText.startsWith("**") && selectedText.length >= 2) {
                        // Toggle OFF
                        val unwrapped = selectedText.substring(1, selectedText.length - 1)
                        val newText = text.replaceRange(start, end, unwrapped)
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(start, start + unwrapped.length)
                        )
                    } else {
                        val wrapped = "*$selectedText*"
                        val newText = text.replaceRange(start, end, wrapped)
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(start + 1, start + 1 + selectedText.length)
                        )
                    }
                } else {
                    val insert = "**"
                    val newText = text.replaceRange(start, end, insert)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 1)
                    )
                }
            }

            MarkdownAction.HEADING -> {
                if (hasSelection) {
                    val lines = selectedText.lines()
                    val allHeadings = lines.all { it.isBlank() || it.startsWith("### ") }
                    val transformed = if (allHeadings) {
                        lines.joinToString("\n") { it.removePrefix("### ") }
                    } else {
                        lines.joinToString("\n") { line ->
                            if (line.isBlank()) line
                            else if (line.startsWith("### ")) line
                            else "### " + line.trimStart('#').trimStart()
                        }
                    }
                    val needsNewline = start > 0 && text[start - 1] != '\n' && !allHeadings
                    val replacement = if (needsNewline) "\n$transformed" else transformed
                    val newText = text.replaceRange(start, end, replacement)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + replacement.length)
                    )
                } else {
                    val prefix = if (start > 0 && text[start - 1] != '\n') "\n### " else "### "
                    val newText = text.replaceRange(start, end, prefix)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + prefix.length)
                    )
                }
            }

            MarkdownAction.BULLET_LIST -> {
                if (hasSelection) {
                    val lines = selectedText.lines()
                    val allBulleted = lines.all { it.isBlank() || it.startsWith("- ") }
                    val transformed = if (allBulleted) {
                        lines.joinToString("\n") { it.removePrefix("- ") }
                    } else {
                        lines.joinToString("\n") { line ->
                            if (line.isBlank()) line
                            else if (line.startsWith("- ")) line
                            else "- $line"
                        }
                    }
                    val needsNewline = start > 0 && text[start - 1] != '\n' && !allBulleted
                    val replacement = if (needsNewline) "\n$transformed" else transformed
                    val newText = text.replaceRange(start, end, replacement)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + replacement.length)
                    )
                } else {
                    val prefix = if (start > 0 && text[start - 1] != '\n') "\n- " else "- "
                    val newText = text.replaceRange(start, end, prefix)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + prefix.length)
                    )
                }
            }

            MarkdownAction.TASK_CHECKBOX -> {
                if (hasSelection) {
                    val lines = selectedText.lines()
                    val allCheckedOrUnchecked = lines.all { it.isBlank() || it.startsWith("- [ ] ") || it.startsWith("- [x] ") }
                    val transformed = if (allCheckedOrUnchecked) {
                        lines.joinToString("\n") { it.removePrefix("- [ ] ").removePrefix("- [x] ") }
                    } else {
                        lines.joinToString("\n") { line ->
                            if (line.isBlank()) line
                            else if (line.startsWith("- [ ] ") || line.startsWith("- [x] ")) line
                            else if (line.startsWith("- ")) "- [ ] " + line.removePrefix("- ")
                            else "- [ ] $line"
                        }
                    }
                    val needsNewline = start > 0 && text[start - 1] != '\n' && !allCheckedOrUnchecked
                    val replacement = if (needsNewline) "\n$transformed" else transformed
                    val newText = text.replaceRange(start, end, replacement)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + replacement.length)
                    )
                } else {
                    val prefix = if (start > 0 && text[start - 1] != '\n') "\n- [ ] " else "- [ ] "
                    val newText = text.replaceRange(start, end, prefix)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + prefix.length)
                    )
                }
            }

            MarkdownAction.QUOTE -> {
                if (hasSelection) {
                    val lines = selectedText.lines()
                    val allQuoted = lines.all { it.isBlank() || it.startsWith("> ") || it == ">" }
                    val transformed = if (allQuoted) {
                        lines.joinToString("\n") {
                            if (it == ">") "" else it.removePrefix("> ")
                        }
                    } else {
                        lines.joinToString("\n") { line ->
                            if (line.isBlank()) ">"
                            else if (line.startsWith("> ")) line
                            else "> $line"
                        }
                    }
                    val needsNewline = start > 0 && text[start - 1] != '\n' && !allQuoted
                    val replacement = if (needsNewline) "\n$transformed" else transformed
                    val newText = text.replaceRange(start, end, replacement)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + replacement.length)
                    )
                } else {
                    val prefix = if (start > 0 && text[start - 1] != '\n') "\n> " else "> "
                    val newText = text.replaceRange(start, end, prefix)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + prefix.length)
                    )
                }
            }

            MarkdownAction.CODE -> {
                if (hasSelection) {
                    if (selectedText.contains("\n")) {
                        // Multi-line code block
                        val replacement = "```\n$selectedText\n```"
                        val newText = text.replaceRange(start, end, replacement)
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(start + 4, start + 4 + selectedText.length)
                        )
                    } else {
                        // Single-line inline code
                        if (selectedText.startsWith("`") && selectedText.endsWith("`") && selectedText.length >= 2) {
                            val unwrapped = selectedText.substring(1, selectedText.length - 1)
                            val newText = text.replaceRange(start, end, unwrapped)
                            TextFieldValue(
                                text = newText,
                                selection = TextRange(start, start + unwrapped.length)
                            )
                        } else {
                            val wrapped = "`$selectedText`"
                            val newText = text.replaceRange(start, end, wrapped)
                            TextFieldValue(
                                text = newText,
                                selection = TextRange(start + 1, start + 1 + selectedText.length)
                            )
                        }
                    }
                } else {
                    val insert = "\n```\n\n```"
                    val newText = text.replaceRange(start, end, insert)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 5)
                    )
                }
            }

            MarkdownAction.HASHTAG -> {
                if (hasSelection) {
                    val trimmed = selectedText.trim()
                    val replacement = if (trimmed.startsWith("#")) {
                        trimmed.removePrefix("#")
                    } else {
                        "#" + trimmed.replace(" ", " #")
                    }
                    val newText = text.replaceRange(start, end, replacement)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + replacement.length)
                    )
                } else {
                    val prefix = if (start > 0 && text[start - 1] != ' ' && text[start - 1] != '\n') " #" else "#"
                    val newText = text.replaceRange(start, end, prefix)
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + prefix.length)
                    )
                }
            }

            MarkdownAction.TIMESTAMP -> {
                val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(currentTimeMillis))
                val formattedTime = "**$timeString** - "
                val replacement = if (hasSelection) {
                    formattedTime + selectedText
                } else {
                    val prefix = if (start > 0 && text[start - 1] != ' ' && text[start - 1] != '\n') " " else ""
                    prefix + formattedTime
                }
                val newText = text.replaceRange(start, end, replacement)
                TextFieldValue(
                    text = newText,
                    selection = TextRange(start + replacement.length)
                )
            }
        }
    }
}

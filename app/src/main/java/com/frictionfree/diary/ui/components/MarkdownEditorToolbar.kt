package com.frictionfree.diary.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarkdownEditorToolbar(
    modifier: Modifier = Modifier,
    onInsertText: (String, Int) -> Unit // (text, cursorOffsetFromStart)
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Bold
            FilledTonalIconButton(
                onClick = { onInsertText("****", 2) },
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(20.dp))
            }

            // Italic
            FilledTonalIconButton(
                onClick = { onInsertText("**", 1) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(20.dp))
            }

            // Heading
            FilledTonalIconButton(
                onClick = { onInsertText("\n### ", 5) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Title, contentDescription = "Heading", modifier = Modifier.size(20.dp))
            }

            // Bullet List
            FilledTonalIconButton(
                onClick = { onInsertText("\n- ", 3) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "List", modifier = Modifier.size(20.dp))
            }

            // Task Checkbox
            FilledTonalIconButton(
                onClick = { onInsertText("\n- [ ] ", 7) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.CheckBox, contentDescription = "Task Checkbox", modifier = Modifier.size(20.dp))
            }

            // Quote
            FilledTonalIconButton(
                onClick = { onInsertText("\n> ", 3) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatQuote, contentDescription = "Quote", modifier = Modifier.size(20.dp))
            }

            // Code
            FilledTonalIconButton(
                onClick = { onInsertText("\n```\n\n```", 5) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Code, contentDescription = "Code Block", modifier = Modifier.size(20.dp))
            }

            // Hashtag
            FilledTonalIconButton(
                onClick = { onInsertText(" #", 2) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Tag, contentDescription = "Hashtag", modifier = Modifier.size(20.dp))
            }

            // Insert Current Time
            FilledTonalIconButton(
                onClick = {
                    val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    onInsertText(" **$timeString** - ", timeString.length + 7)
                },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = "Insert Current Time", modifier = Modifier.size(20.dp))
            }
        }
    }
}

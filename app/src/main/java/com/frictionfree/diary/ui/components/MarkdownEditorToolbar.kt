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
    onAction: (MarkdownAction) -> Unit
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
                onClick = { onAction(MarkdownAction.BOLD) },
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(20.dp))
            }

            // Italic
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.ITALIC) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(20.dp))
            }

            // Heading
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.HEADING) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Title, contentDescription = "Heading", modifier = Modifier.size(20.dp))
            }

            // Bullet List
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.BULLET_LIST) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "List", modifier = Modifier.size(20.dp))
            }

            // Task Checkbox
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.TASK_CHECKBOX) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.CheckBox, contentDescription = "Task Checkbox", modifier = Modifier.size(20.dp))
            }

            // Quote
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.QUOTE) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.FormatQuote, contentDescription = "Quote", modifier = Modifier.size(20.dp))
            }

            // Code
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.CODE) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Code, contentDescription = "Code Block", modifier = Modifier.size(20.dp))
            }

            // Hashtag
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.HASHTAG) },
                modifier = Modifier.padding(start = 6.dp).size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Tag, contentDescription = "Hashtag", modifier = Modifier.size(20.dp))
            }

            // Insert Current Time
            FilledTonalIconButton(
                onClick = { onAction(MarkdownAction.TIMESTAMP) },
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

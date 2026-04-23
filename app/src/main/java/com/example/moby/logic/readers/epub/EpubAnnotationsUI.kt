package com.example.moby.logic.readers.epub

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun EpubSelectionPopup(
    xdp: Float,
    ydp: Float,
    selectedText: String,
    onHighlight: (String) -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
    ) {
        Surface(
            modifier = Modifier
                .offset { 
                    with(density) { 
                        IntOffset((xdp - 90).coerceAtLeast(16f).dp.roundToPx(), (ydp - 240).coerceAtLeast(64f).dp.roundToPx()) 
                    } 
                }
                .width(220.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // TOP ACTIONS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SelectionActionItem(Icons.AutoMirrored.Filled.NoteAdd, "Note") { /* TODO */ }
                    SelectionActionItem(Icons.Default.Circle, "Highlight", tint = Color(0xFFFFF176)) { onHighlight("#FFF59D") }
                    SelectionActionItem(Icons.Default.ContentCopy, "Copy") { onCopy() }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // LIST ACTIONS
                SelectionListItem(Icons.Default.Translate, "Translate") {
                    val encoded = java.net.URLEncoder.encode(selectedText, "UTF-8")
                    uriHandler.openUri("https://translate.google.com/?sl=auto&tl=es&text=$encoded&op=translate")
                    onDismiss()
                }
                SelectionListItem(Icons.Default.MenuBook, "Dictionary") { /* TODO */ }
                SelectionListItem(Icons.Default.Share, "Share") { /* TODO */ }
            }
        }
    }
}

@Composable
fun SelectionActionItem(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            icon, 
            contentDescription = label, 
            modifier = Modifier.size(24.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
fun SelectionListItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label, 
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (label == "Dictionary") {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        }
    }
}

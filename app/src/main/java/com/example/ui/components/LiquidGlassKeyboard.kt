package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardCapslock
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicSpacing

@Composable
fun LiquidGlassKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShiftActive by remember { mutableStateOf(false) }
    var isSymbolsActive by remember { mutableStateOf(false) }
    var symbolsPage by remember { mutableIntStateOf(0) }

    val lettersRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val lettersRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val lettersRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    val symbolRows = if (symbolsPage == 0) SYMBOLS_PAGE_1 else SYMBOLS_PAGE_2
    val row1 = if (isSymbolsActive) symbolRows[0] else lettersRow1
    val row2 = if (isSymbolsActive) symbolRows[1] else lettersRow2
    val row3 = if (isSymbolsActive) symbolRows[2] else lettersRow3

    val processKey: (String) -> String = { key ->
        if (!isSymbolsActive && isShiftActive) key.uppercase() else key
    }

    // Shift is one-shot, like every system keyboard: it applies to the next
    // key and then releases.
    val pressKey: (String) -> Unit = { key ->
        onKeyPress(processKey(key))
        isShiftActive = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row1.forEach { key ->
                KeyboardKey(
                    text = processKey(key),
                    modifier = Modifier.weight(1f),
                    onClick = { pressKey(key) }
                )
            }
        }
        
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row2.forEach { key ->
                KeyboardKey(
                    text = processKey(key),
                    modifier = Modifier.weight(1f),
                    onClick = { pressKey(key) }
                )
            }
        }

        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Shift (letters) / second symbols page toggle (symbols)
            if (isSymbolsActive) {
                KeyboardKey(
                    text = if (symbolsPage == 0) "=\\<" else "1/2",
                    modifier = Modifier.weight(1.5f),
                    onClick = { symbolsPage = if (symbolsPage == 0) 1 else 0 }
                )
            } else {
                KeyboardIconKey(
                    icon = Icons.Filled.KeyboardCapslock,
                    isActive = isShiftActive,
                    modifier = Modifier.weight(1.5f),
                    onClick = { isShiftActive = !isShiftActive }
                )
            }
            
            row3.forEach { key ->
                KeyboardKey(
                    text = processKey(key),
                    modifier = Modifier.weight(1f),
                    onClick = { pressKey(key) }
                )
            }
            
            // Backspace
            KeyboardIconKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                modifier = Modifier.weight(1.5f),
                onClick = onBackspace
            )
        }

        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardKey(
                text = if (isSymbolsActive) "ABC" else "?123",
                modifier = Modifier.weight(1.5f),
                onClick = {
                    isSymbolsActive = !isSymbolsActive
                    symbolsPage = 0
                }
            )
            
            KeyboardKey(
                text = ",",
                modifier = Modifier.weight(1f),
                onClick = { onKeyPress(",") }
            )
            
            KeyboardKey(
                text = "space",
                modifier = Modifier.weight(4f),
                onClick = { onKeyPress(" ") }
            )
            
            KeyboardKey(
                text = ".",
                modifier = Modifier.weight(1f),
                onClick = { onKeyPress(".") }
            )
            
            KeyboardKey(
                text = "Enter",
                variant = GlassVariant.Glow,
                modifier = Modifier.weight(2f),
                onClick = onEnter
            )
        }
    }
}

private val SYMBOLS_PAGE_1 = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"),
    listOf("*", "\"", "'", ":", ";", "!", "?")
)

// Everything a password or URL needs that page 1 lacks (_ = / \ etc.).
private val SYMBOLS_PAGE_2 = listOf(
    listOf("[", "]", "{", "}", "<", ">", "^", "~", "|", "\\"),
    listOf("_", "=", "/", "`", "€", "£", "¥", "§", "°"),
    listOf("…", "«", "»", "¿", "¡", "•", "±")
)

@Composable
fun KeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Floating,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        variant = variant,
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (variant == GlassVariant.Glow) AtomicColors.Foreground else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (variant == GlassVariant.Glow) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun KeyboardIconKey(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val variant = if (isActive) GlassVariant.Glow else GlassVariant.Floating
    LiquidGlassSurface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        variant = variant,
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) AtomicColors.Foreground else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


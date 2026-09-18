package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius

private val KeyGap = 6.dp
private val KeyMinHeight = 48.dp

/**
 * The in-app and system keyboard. Flat 8dp keys on the page background, one
 * white primary key (Enter) -- the reference's structure, without the
 * decorative glass sheen the first version had.
 */
@Composable
fun LiquidGlassKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    enterIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    enterDescription: String = "Enter"
) {
    var isShiftActive by remember { mutableStateOf(false) }
    var isSymbolsActive by remember { mutableStateOf(false) }
    var symbolsPage by remember { mutableIntStateOf(0) }

    val symbolRows = if (symbolsPage == 0) SYMBOLS_PAGE_1 else SYMBOLS_PAGE_2
    val row1 = if (isSymbolsActive) symbolRows[0] else LETTERS_ROW_1
    val row2 = if (isSymbolsActive) symbolRows[1] else LETTERS_ROW_2
    val row3 = if (isSymbolsActive) symbolRows[2] else LETTERS_ROW_3

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
        verticalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            row1.forEach { key ->
                KeyboardKey(text = processKey(key), modifier = Modifier.weight(1f), onClick = { pressKey(key) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            row2.forEach { key ->
                KeyboardKey(text = processKey(key), modifier = Modifier.weight(1f), onClick = { pressKey(key) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            if (isSymbolsActive) {
                KeyboardKey(
                    text = if (symbolsPage == 0) "=\\<" else "1/2",
                    modifier = Modifier.weight(1.5f),
                    variant = GlassVariant.Glow,
                    onClick = { symbolsPage = if (symbolsPage == 0) 1 else 0 }
                )
            } else {
                KeyboardIconKey(
                    icon = Icons.Filled.ArrowUpward,
                    contentDescription = "Shift",
                    isActive = isShiftActive,
                    modifier = Modifier.weight(1.5f),
                    onClick = { isShiftActive = !isShiftActive }
                )
            }

            row3.forEach { key ->
                KeyboardKey(text = processKey(key), modifier = Modifier.weight(1f), onClick = { pressKey(key) })
            }

            KeyboardIconKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                modifier = Modifier.weight(1.5f),
                onClick = onBackspace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            KeyboardKey(
                text = if (isSymbolsActive) "ABC" else "?123",
                modifier = Modifier.weight(1.5f),
                variant = GlassVariant.Glow,
                fontSize = AtomicFontSize.label,
                onClick = {
                    isSymbolsActive = !isSymbolsActive
                    symbolsPage = 0
                }
            )

            KeyboardKey(text = ",", modifier = Modifier.weight(1f), onClick = { onKeyPress(",") })

            KeyboardKey(
                text = "space",
                modifier = Modifier.weight(4f),
                textColor = AtomicColors.TextSecondary,
                fontSize = AtomicFontSize.label,
                onClick = { onKeyPress(" ") }
            )

            KeyboardKey(text = ".", modifier = Modifier.weight(1f), onClick = { onKeyPress(".") })

            KeyboardIconKey(
                icon = enterIcon,
                contentDescription = enterDescription,
                variant = GlassVariant.Primary,
                modifier = Modifier.weight(1.5f),
                onClick = onEnter
            )
        }
    }
}

private val LETTERS_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val LETTERS_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val LETTERS_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m")

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
    textColor: Color = AtomicColors.Foreground,
    fontSize: androidx.compose.ui.unit.TextUnit = 17.sp,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier,
        variant = variant,
        shape = RoundedCornerShape(AtomicRadius.md),
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = KeyMinHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = AtomicFontWeight.medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun KeyboardIconKey(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isActive: Boolean = false,
    variant: GlassVariant? = null,
    onClick: () -> Unit
) {
    val resolved = variant ?: if (isActive) GlassVariant.Primary else GlassVariant.Glow
    val tint = if (resolved == GlassVariant.Primary) AtomicColors.Background else AtomicColors.Foreground
    LiquidGlassSurface(
        modifier = modifier.then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
        ),
        variant = resolved,
        shape = RoundedCornerShape(AtomicRadius.md),
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = KeyMinHeight),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
    }
}

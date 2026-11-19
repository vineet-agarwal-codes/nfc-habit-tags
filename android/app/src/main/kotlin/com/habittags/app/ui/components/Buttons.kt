package com.habittags.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val Pill = RoundedCornerShape(percent = 50)

/** The one primary action on a screen: flat tomato pill (or [container] on a coloured field). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

/** A secondary action: one tone step off the ground, no outline. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** A quiet text action. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = color, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun WideButton(text: String, onClick: () -> Unit, enabled: Boolean = true) =
    PrimaryButton(text, onClick, Modifier.fillMaxWidth().padding(top = 4.dp), enabled)

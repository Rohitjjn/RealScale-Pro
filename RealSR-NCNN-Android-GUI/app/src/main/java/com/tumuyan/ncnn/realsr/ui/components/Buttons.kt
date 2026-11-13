package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import androidx.compose.foundation.BorderStroke

enum class ButtonVariant { Primary, Secondary, Tonal, Ghost, Dark, Danger }
enum class ButtonSize { Lg, Md, Sm }

/**
 * RealScale button — exact port of the design's <Button/>:
 * lg = min-h-13 px-5 text-base semibold (52dp) · md = min-h-11 px-4 0.9375rem (44dp) · sm = min-h-9 px-3 text-sm (36dp)
 * rounded-md (8dp), 2dp gap, states: pressed / disabled.
 */
@Composable
fun RealScaleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: ButtonSize = ButtonSize.Md,
    full: Boolean = false,
    enabled: Boolean = true,
    icon: Painter? = null,
    text: String,
    contentDescription: String? = null,
) {
    val c = LocalRealScaleColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val (bg, fg) = when {
        !enabled && variant == ButtonVariant.Primary -> c.disabled to c.onDisabled
        !enabled -> Color.Transparent to c.mutedSoft
        pressed && variant == ButtonVariant.Primary -> c.primaryActive to c.onPrimary
        pressed && variant == ButtonVariant.Secondary -> c.soft to c.ink
        pressed && variant == ButtonVariant.Tonal -> c.strong to c.ink
        pressed && variant == ButtonVariant.Ghost -> c.card to c.ink
        pressed && variant == ButtonVariant.Dark -> c.darkSoft to c.onDark
        pressed && variant == ButtonVariant.Danger -> c.soft to c.error
        variant == ButtonVariant.Primary -> c.primary to c.onPrimary
        variant == ButtonVariant.Secondary -> c.canvas to c.ink
        variant == ButtonVariant.Tonal -> c.card to c.ink
        variant == ButtonVariant.Ghost -> Color.Transparent to c.ink
        variant == ButtonVariant.Dark -> c.darkElevated to c.onDark
        variant == ButtonVariant.Danger -> c.canvas to c.error
        else -> c.canvas to c.ink
    }
    val border = if (variant == ButtonVariant.Secondary || variant == ButtonVariant.Danger) {
        BorderStroke(1.dp, c.hairline)
    } else null

    val shape = RealScaleShape.Md
    val minH = when (size) { ButtonSize.Lg -> 52.dp; ButtonSize.Md -> 44.dp; ButtonSize.Sm -> 36.dp }
    val padH = when (size) { ButtonSize.Lg -> 20.dp; ButtonSize.Md -> 16.dp; ButtonSize.Sm -> 12.dp }
    val style: TextStyle = when (size) {
        ButtonSize.Lg -> RealScaleType.Body16Medium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        ButtonSize.Md -> RealScaleType.Body15Medium
        ButtonSize.Sm -> RealScaleType.Body14Medium
    }
    val iconSize = when (size) { ButtonSize.Lg -> 22.dp; ButtonSize.Md -> 20.dp; ButtonSize.Sm -> 16.dp }

    Row(
        modifier = modifier
            .then(if (full) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .then(border?.let { Modifier.border(it, shape) } ?: Modifier)
            .background(bg)
            .heightIn(min = minH)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = padH),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = icon, contentDescription = null,
                modifier = Modifier.size(iconSize), contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg),
            )
        }
        if (icon != null) androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(text, style = style, color = fg)
    }
}

enum class IconButtonVariant { Outline, Plain, Dark }

/** 40dp circular icon button (design: outline / plain / dark) with a 48dp touch target. */
@Composable
fun RealScaleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IconButtonVariant = IconButtonVariant.Outline,
    enabled: Boolean = true,
    contentDescription: String,
    icon: Painter,
) {
    val c = LocalRealScaleColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        variant == IconButtonVariant.Outline && pressed -> c.soft
        variant == IconButtonVariant.Outline -> c.canvas
        variant == IconButtonVariant.Plain && pressed -> c.card
        variant == IconButtonVariant.Plain -> Color.Transparent
        variant == IconButtonVariant.Dark && pressed -> c.darkSoft
        else -> c.darkElevated.copy(alpha = 0.9f)
    }
    val tint = if (!enabled) c.mutedSoft else if (variant == IconButtonVariant.Dark) c.onDark else c.ink
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .then(if (variant == IconButtonVariant.Outline) Modifier.border(1.dp, c.hairline, shape) else Modifier)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = icon, contentDescription = contentDescription,
                modifier = Modifier.size(22.dp), contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
            )
        }
    }
}

package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RadioMark
import com.tumuyan.ncnn.realsr.design.RsType

/**
 * The one text input in the app. The design system has an [RsField] *label* but no input, because
 * until now nothing needed typing except the feedback box. The AI screens need three: the key, the
 * label and the prompt — so this sits beside them rather than in `design/`.
 */
@Composable
fun AiInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Int = 48,
) {
    val c = LocalRsColors.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                placeholder,
                style = RsType.body(15f, lineHeightRatio = 1.5f),
                color = c.mutedSoft,
                maxLines = if (singleLine) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = RsType.body(15f, lineHeightRatio = 1.5f).copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled input, so every AI form reads the same. */
@Composable
fun AiInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    secret: Boolean = false,
    minHeight: Int = 48,
) {
    val c = LocalRsColors.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = RsType.body(14f, FontWeight.Medium, 1.3f), color = c.ink)
        AiInput(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(top = 8.dp),
            placeholder = placeholder,
            singleLine = singleLine,
            secret = secret,
            minHeight = minHeight,
        )
        if (hint != null) {
            Text(
                hint,
                style = RsType.body(12f, lineHeightRatio = 1.45f),
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Tappable provider/model option used by the AI sheets. */
@Composable
fun AiOptionRow(
    title: String,
    sub: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.md.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.soft else c.canvas)
            .border(1.dp, if (selected) c.inverse else c.hairline, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RsType.body(15f, FontWeight.Medium, 1.3f), color = c.ink)
            if (sub != null) {
                Text(
                    sub,
                    style = RsType.body(12f, lineHeightRatio = 1.45f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        trailing?.invoke()
        if (selected) {
            RadioMark(
                checked = true,
                borderColor = c.inverse,
                fillColor = c.inverse,
                tint = c.canvas,
            )
        }
    }
}

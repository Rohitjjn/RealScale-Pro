package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.PreflightVerdict
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing

@Composable
fun PreflightAdvisoryDialog(
    advisory: PreflightVerdict.Advisory,
    onApplyMitigation: (PreflightMitigation) -> Unit,
    onProceedAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.canvas,
            border = BorderStroke(1.dp, colors.hairline),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                EditorialOverline(text = "Before enhancing")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = advisory.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = advisory.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    advisory.mitigations.forEachIndexed { index, mitigation ->
                        if (index == 0) {
                            TerracottaPrimaryButton(
                                text = mitigation.label,
                                onClick = { onApplyMitigation(mitigation) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            QuietOutlineButton(
                                text = mitigation.label,
                                onClick = { onApplyMitigation(mitigation) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (advisory.canProceedAnyway) {
                        QuietOutlineButton(
                            text = "Continue anyway",
                            tint = colors.muted,
                            onClick = onProceedAnyway,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    QuietOutlineButton(
                        text = "Cancel",
                        tint = colors.muted,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

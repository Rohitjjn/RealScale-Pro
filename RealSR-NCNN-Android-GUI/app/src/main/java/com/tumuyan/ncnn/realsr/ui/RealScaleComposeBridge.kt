package com.tumuyan.ncnn.realsr.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Minimal Compose verification bridge introduced in Step 2 alongside upstream Java/XML.
 */
@Composable
fun RealScaleComposeStatusBadge(label: String) {
    MaterialTheme {
        Text(text = label)
    }
}

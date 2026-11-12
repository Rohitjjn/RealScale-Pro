package dev.realscale.pro.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.*
import dev.realscale.pro.ui.theme.Warm

@Composable fun WarmPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, Modifier.height(40.dp), enabled, MaterialTheme.shapes.medium,
        ButtonDefaults.buttonColors(Warm.Primary, Warm.OnPrimary, Warm.PrimaryDisabled, Warm.Muted)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }

@Composable fun WarmSecondaryButton(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick, Modifier.height(40.dp), true, MaterialTheme.shapes.medium, border = BorderStroke(1.dp, Warm.Hairline)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }

@Composable fun BadgePill(text: String, coral: Boolean = false) =
    Box(Modifier.background(if (coral) Warm.Primary else Warm.SurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(9999.dp))
        .padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
            color = if (coral) Warm.OnPrimary else MaterialTheme.colorScheme.onSurface)
    }

@Composable fun FeatureCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Card(modifier, MaterialTheme.shapes.large, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }

@Composable fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Card(modifier, MaterialTheme.shapes.large, CardDefaults.cardColors(Warm.Dark), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }

@Composable fun CalmProgress(fraction: Float, label: String, eta: String, onCancel: () -> Unit) {
    DarkCard(Modifier.fillMaxWidth()) {
        Text("Processing", style = MaterialTheme.typography.displaySmall, color = Warm.OnDark)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Warm.OnDarkSoft)
        LinearProgressIndicator({ fraction.coerceIn(0.02f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
            color = Warm.Primary, trackColor = Warm.DarkElevated)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${(fraction * 100).toInt()}% · $eta", style = MaterialTheme.typography.labelMedium, color = Warm.OnDarkSoft)
            TextButton(onCancel) { Text("Cancel", color = Warm.Primary) }
        }
    }
}

/** Draggable split before/after canvas with pinch-to-zoom. */
@Composable fun BeforeAfterCanvas(before: Bitmap, after: Bitmap, modifier: Modifier = Modifier) {
    var split by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var widthPx by remember { mutableIntStateOf(1) }
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }
    Box(modifier.clip(MaterialTheme.shapes.extraLarge).background(Warm.SurfaceCard)
        .onSizeChanged { widthPx = it.width }
        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset += pan } }
        .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> split = (change.position.x / widthPx).coerceIn(0.05f, 0.95f) } }) {
        val layer: Modifier.() -> Modifier = { graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y } }
        Image(afterImg, "After", Modifier.fillMaxSize().layer(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Image(beforeImg, "Before", Modifier.fillMaxSize().layer()
            .clip(object : Shape { override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Rectangle(Rect(0f, 0f, size.width * split, size.height)) }),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Box(Modifier.fillMaxHeight().width(2.dp).graphicsLayer { translationX = widthPx * split }.background(Warm.Primary))
        Box(Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = widthPx * split - 18.dp.toPx() }
            .size(36.dp).background(Warm.Canvas, CircleShape).border(1.dp, Warm.Hairline, CircleShape),
            Alignment.Center) { Text("↔", color = Warm.Ink, style = MaterialTheme.typography.titleSmall) }
        Row(Modifier.align(Alignment.TopStart).padding(12.dp)) { BadgePill("Before") }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) { BadgePill("After", coral = true) }
    }
}

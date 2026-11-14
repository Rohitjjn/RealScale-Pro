package com.tumuyan.ncnn.realsr.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tumuyan.ncnn.realsr.MainActivity
import com.tumuyan.ncnn.realsr.ProcessingService
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import com.tumuyan.ncnn.realsr.core.engine.OomSafeImageDecoders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val WidgetCanvasColor = ColorProvider(day = Color(0xFFFAF9F5), night = Color(0xFF181715))
private val WidgetSurfaceColor = ColorProvider(day = Color(0xFFF1EEE6), night = Color(0xFF23211E))
private val WidgetInkColor = ColorProvider(day = Color(0xFF141413), night = Color(0xFFF3F1EA))
private val WidgetMutedColor = ColorProvider(day = Color(0xFF6B6860), night = Color(0xFFA7A299))
private val WidgetTerracottaColor = ColorProvider(day = Color(0xFFBD4707), night = Color(0xFFD6682E))
private val WidgetOnTerracottaColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF141413))

/**
 * Small Glance Home-Screen Widget: one-tap "Enhance a photo" launcher into RealScale-Pro Studio.
 */
class RealScaleEnhanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enhanceIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("realscale://widget/enhance")
            putExtra("android.intent.extra.OPEN_PHOTO_PICKER", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(22.dp)
                        .background(WidgetCanvasColor)
                        .clickable(actionStartActivity(enhanceIntent))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.brand_logo_medallion),
                            contentDescription = "RealScale-Pro",
                            modifier = GlanceModifier.size(32.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = "RealScale-Pro",
                            style = TextStyle(
                                color = WidgetMutedColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(14.dp)
                            .background(WidgetTerracottaColor)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enhance a photo",
                            style = TextStyle(
                                color = WidgetOnTerracottaColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

class RealScaleEnhanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RealScaleEnhanceWidget()
}

private data class WidgetCreationTile(
    val entity: RecentCreationEntity,
    val thumbnail: Bitmap?
)

/**
 * Medium Glance Home-Screen Widget: shows up to 3 recent upscaled creations with direct result deep-links.
 */
class RealScaleRecentWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tiles = withContext(Dispatchers.IO) {
            runCatching {
                val recent = RealScaleAppContainer.getInstance(context)
                    .database
                    .recentCreationDao()
                    .getRecentOnce(limit = 3)
                recent.map { item ->
                    val bmp = OomSafeImageDecoders
                        .decodeDownsampledPreview(File(item.outputPath), maxEdgePx = 112)
                        ?.bitmap
                    WidgetCreationTile(entity = item, thumbnail = bmp)
                }
            }.getOrDefault(emptyList())
        }

        val enhanceIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("realscale://widget/enhance_medium")
            putExtra("android.intent.extra.OPEN_PHOTO_PICKER", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(22.dp)
                        .background(WidgetCanvasColor)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.brand_logo_medallion),
                            contentDescription = "RealScale-Pro",
                            modifier = GlanceModifier.size(24.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = "Recent creations",
                            style = TextStyle(
                                color = WidgetInkColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(WidgetTerracottaColor)
                                .clickable(actionStartActivity(enhanceIntent))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Enhance",
                                style = TextStyle(
                                    color = WidgetOnTerracottaColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    if (tiles.isEmpty()) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .cornerRadius(14.dp)
                                .background(WidgetSurfaceColor)
                                .clickable(actionStartActivity(enhanceIntent))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap Enhance to upscale your first photo offline",
                                style = TextStyle(
                                    color = WidgetMutedColor,
                                    fontSize = 12.sp
                                ),
                                maxLines = 2
                            )
                        }
                    } else {
                        Row(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tiles.forEachIndexed { idx, tile ->
                                if (idx > 0) {
                                    Spacer(modifier = GlanceModifier.width(8.dp))
                                }
                                val openResultIntent = Intent(context, MainActivity::class.java).apply {
                                    action = Intent.ACTION_VIEW
                                    data = Uri.parse("realscale://widget/creation/${tile.entity.id}")
                                    putExtra(ProcessingService.EXTRA_OPEN_CREATION_ID, tile.entity.id)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                Column(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .fillMaxHeight()
                                        .cornerRadius(14.dp)
                                        .background(WidgetSurfaceColor)
                                        .clickable(actionStartActivity(openResultIntent))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (tile.thumbnail != null) {
                                        Image(
                                            provider = ImageProvider(tile.thumbnail),
                                            contentDescription = tile.entity.presetLabel,
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .defaultWeight()
                                                .cornerRadius(10.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .defaultWeight()
                                                .cornerRadius(10.dp)
                                                .background(WidgetCanvasColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${tile.entity.scale}×",
                                                style = TextStyle(
                                                    color = WidgetInkColor,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                    Text(
                                        text = tile.entity.presetLabel,
                                        style = TextStyle(
                                            color = WidgetInkColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class RealScaleRecentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RealScaleRecentWidget()
}

object RealScaleWidgetsUpdater {
    suspend fun refreshAll(context: Context) {
        runCatching {
            RealScaleEnhanceWidget().updateAll(context)
            RealScaleRecentWidget().updateAll(context)
        }
    }
}

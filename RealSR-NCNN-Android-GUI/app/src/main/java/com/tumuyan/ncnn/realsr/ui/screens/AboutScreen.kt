package com.tumuyan.ncnn.realsr.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.BuildConfig
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleMark
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.ShareAppSheet
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/** About — app info, version, open-source licenses, share. */
@Composable
fun AboutScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    var shareOpen by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { viewModel.showToast("No browser available.") }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RealScaleIconButton(
                        onClick = { navController.popBackStack() },
                        variant = IconButtonVariant.Dark,
                        contentDescription = "Back",
                        icon = painterResource(R.drawable.ic_lucide_chevron_left),
                    )
                    Text("About", style = RealScaleType.Display24, color = c.ink)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RealScaleMark(size = 40.dp)
                Column {
                    Text("RealScale-Pro", style = RealScaleType.Display26, color = c.ink)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = RealScaleType.Body13, color = c.muted)
                }
            }
            Text(
                "Sharper, bigger photos — entirely on your device. No account, no upload, no cloud. " +
                    "Models download once and run offline through ncnn.",
                style = RealScaleType.Body15,
                color = c.body,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            Text("Open-source licenses", style = RealScaleType.Body15Medium, color = c.ink, modifier = Modifier.padding(bottom = 8.dp))
            LICENSES.forEachIndexed { index, (name, license, about) ->
                if (index > 0) RealScaleDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(name, style = RealScaleType.Body14Medium, color = c.ink)
                        Text("$license · $about", style = RealScaleType.Body13, color = c.muted)
                    }
                    RealScaleBadge(text = license.split("-").first().capitalize(), tone = com.tumuyan.ncnn.realsr.ui.components.BadgeTone.Outline)
                }
            }
            Spacer(Modifier.height(24.dp))

            RealScaleGroup {
                RealScaleRow(
                    title = "Share the app",
                    sub = "Help others find RealScale-Pro.",
                    icon = painterResource(R.drawable.ic_lucide_share_2),
                    chevron = true,
                    onClick = { shareOpen = true },
                )
                RealScaleDivider()
                RealScaleRow(
                    title = "Privacy",
                    sub = "Your images never leave this device.",
                    icon = painterResource(R.drawable.ic_lucide_shield_check),
                    chevron = true,
                    onClick = { openUrl("https://github.com/Rohitjjn/RealScale-Pro#privacy") },
                )
                RealScaleDivider()
                RealScaleRow(
                    title = "GitHub repository",
                    sub = "Source code and issue tracker.",
                    icon = null,
                    chevron = true,
                    onClick = { openUrl("https://github.com/Rohitjjn/RealScale-Pro") },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    ShareAppSheet(open = shareOpen, onClose = { shareOpen = false }, viewModel = viewModel)
}

private fun String.capitalize(): String = if (isEmpty()) this else this.substring(0, 1).uppercase() + this.substring(1)

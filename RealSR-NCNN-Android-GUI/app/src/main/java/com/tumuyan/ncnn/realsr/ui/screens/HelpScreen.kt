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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/** Help — FAQ plus feedback routes (mailto + GitHub issues). No fake support channels. */
@Composable
fun HelpScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current

    fun mailFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "RealScale-Pro feedback")
            putExtra(Intent.EXTRA_TEXT, "\n\n---\nApp version: ${context.packageName}")
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Send feedback")) }
            .onFailure { viewModel.showToast("No email app available.") }
    }

    fun openIssues() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Rohitjjn/RealScale-Pro/issues"))
        runCatching { context.startActivity(intent) }
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
                    Text("Help", style = RealScaleType.Display24, color = c.ink)
                    Spacer(Modifier.weight(1f))
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
            Text("Frequently asked questions", style = RealScaleType.Body15Medium, color = c.ink)
            Spacer(Modifier.height(12.dp))
            FAQ.forEachIndexed { index, (question, answer) ->
                if (index > 0) com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).clickable { },
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("Q", style = RealScaleType.Body13Strong, color = c.ink, modifier = Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(question, style = RealScaleType.Body15Medium, color = c.ink)
                            Text(answer, style = RealScaleType.Body14, color = c.body, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Text("Get in touch", style = RealScaleType.Body15Medium, color = c.ink)
            Spacer(Modifier.height(12.dp))
            RealScaleGroup {
                RealScaleRow(
                    title = "Email the developer",
                    sub = "Opens your email app with a feedback subject.",
                    icon = null,
                    chevron = true,
                    onClick = { mailFeedback() },
                )
                RealScaleDivider()
                RealScaleRow(
                    title = "GitHub issues",
                    sub = "Report bugs or request features on GitHub.",
                    icon = null,
                    chevron = true,
                    onClick = { openIssues() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

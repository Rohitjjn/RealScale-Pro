package com.tumuyan.ncnn.realsr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tumuyan.ncnn.realsr.ui.RealScaleApp
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            consumeIncomingIntent(intent)
        }
        setContent {
            RealScaleApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingIntent(intent)
    }

    private fun consumeIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        when (incoming.action) {
            Intent.ACTION_SEND -> {
                val single = extractParcelableUri(incoming) ?: incoming.data
                if (single != null) viewModel.acceptShareIntent(listOf(single))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = extractParcelableUriList(incoming).ifEmpty { listOfNotNull(incoming.data) }
                if (list.isNotEmpty()) viewModel.acceptShareIntent(list)
            }
            Intent.ACTION_VIEW -> {
                val dataUri = incoming.data
                if (dataUri != null) viewModel.acceptShareIntent(listOf(dataUri))
            }
        }
    }

    private fun extractParcelableUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extractParcelableUriList(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
}

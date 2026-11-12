package dev.realscale.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.realscale.pro.ui.MainViewModel
import dev.realscale.pro.ui.screens.*
import dev.realscale.pro.ui.theme.RealScaleTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent {
            RealScaleTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                        NavigationBarItem(tab == 0, { tab = 0 }, icon = { Text("✦") }, label = { Text("Studio") })
                        NavigationBarItem(tab == 1, { tab = 1 }, icon = { Text("▦") }, label = { Text("Model Hub") })
                    }
                }) { pad ->
                    Surface(Modifier.padding(pad), color = MaterialTheme.colorScheme.background) {
                        if (tab == 0) HomeScreen(vm) else ModelHubScreen(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShare(intent) }

    private fun handleShare(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            else -> null
        }
        uri?.let(vm::onSharedImage)
    }
}

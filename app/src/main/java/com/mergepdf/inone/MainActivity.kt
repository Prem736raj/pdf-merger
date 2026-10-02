package com.mergepdf.inone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mergepdf.inone.ads.ConsentManager
import com.mergepdf.inone.ads.InterstitialAdManager
import com.mergepdf.inone.ui.AppThemeMode
import com.mergepdf.inone.ui.MainScreen
import com.mergepdf.inone.ui.PdfMergerViewModel
import com.mergepdf.inone.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PdfMergerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureWebViewDirectories()
        com.mergepdf.inone.util.PdfMergerEngine.init(this)

        // Request UMP consent and initialize MobileAds SDK once allowed
        ConsentManager.requestConsentAndInitialize(this) {
            InterstitialAdManager.preloadAd(this)
        }

        handleIncomingPdfIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDarkTheme = when (uiState.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingPdfIntent(intent)
    }

    private fun handleIncomingPdfIntent(intent: Intent?) {
        if (intent == null) return
        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it.filterNotNull()) }
            }
        }

        if (uris.isNotEmpty()) {
            viewModel.addDocumentsFromUris(uris)
        }
    }

    /**
     * Pre-creates WebView HTTP and JavaScript code cache directories to prevent
     * Chromium simple_file_enumerator ENOENT errors during initial index construction.
     */
    private fun ensureWebViewDirectories() {
        try {
            val baseCache = cacheDir ?: return
            val webViewDirs = listOf(
                "WebView/Default/HTTP Cache/Code Cache/js",
                "WebView/Default/HTTP Cache/Code Cache/wasm",
                "WebView/Default/Code Cache/js",
                "WebView/Default/Code Cache/wasm"
            )
            for (dirPath in webViewDirs) {
                val dir = java.io.File(baseCache, dirPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        } catch (_: Exception) {
            // Non-critical cache directory initialization
        }
    }
}

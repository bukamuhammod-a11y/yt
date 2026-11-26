package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.player.PlayerManager
import java.io.ByteArrayInputStream

/**
 * Lightweight 1.dp headless WebView used solely when the user navigates away from a
 * YouTube watch page to browse Home/Search, allowing audio playback and Play/Pause/Next
 * controls to continue uninterrupted in the docked MiniPlayerBar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowseAudioWebView(
    modifier: Modifier = Modifier
) {
    val request by PlayerManager.browseVideoRequest.collectAsState()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastHandledRequestId by remember { mutableLongStateOf(-1L) }

    // Only instantiate once a browse handoff has actually been requested
    var everActivated by remember { mutableStateOf(false) }
    if (request != null && !everActivated) {
        everActivated = true
    }
    if (!everActivated) return

    LaunchedEffect(request, webViewInstance) {
        val wv = webViewInstance ?: return@LaunchedEffect
        val req = request
        if (req == null) {
            if (lastHandledRequestId != -1L) {
                lastHandledRequestId = -1L
                wv.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.pause();}})();",
                    null
                )
                wv.loadUrl("about:blank")
            }
        } else if (req.requestId != lastHandledRequestId) {
            lastHandledRequestId = req.requestId
            val timeParam = if (req.startSeconds > 2) "&t=${req.startSeconds}s" else ""
            wv.loadUrl("https://m.youtube.com/watch?v=${req.videoId}$timeParam")
        }
    }

    AndroidView(
        modifier = modifier
            .size(1.dp)
            .alpha(0f),
        factory = { context ->
            PersistentBackgroundWebView(context).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(1, 1)
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowContentAccess = true
                    allowFileAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun showKeyboard() {
                            // Headless browse player never triggers keyboard
                        }

                        @JavascriptInterface
                        fun onVideoStatus(
                            onWatch: Boolean,
                            isPlaying: Boolean,
                            positionSec: Float,
                            durationSec: Float,
                            title: String,
                            channel: String,
                            url: String
                        ) {
                            post {
                                if (!onWatch || url == "about:blank") return@post
                                val videoId = YouTubeCatalogExtract(url)
                                PlayerManager.updateBrowseWebViewState(
                                    videoId = videoId,
                                    isPlaying = isPlaying,
                                    positionSec = positionSec,
                                    durationSec = durationSec,
                                    title = title,
                                    channel = channel
                                )
                            }
                        }
                    },
                    "AndroidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null && url != "about:blank") {
                            injectEarlyAdBlockScript(view)
                        }
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        if (isYouTubeAdRequest(url)) {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null && url != "about:blank") {
                            injectAntiBotAndBackgroundScript(view)
                            if (PlayerManager.browseVideoRequest.value?.autoPlay == true) {
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        var attempts = 0;
                                        var tryPlay = setInterval(function() {
                                            attempts++;
                                            var v = document.querySelector('video');
                                            if (v) {
                                                if (v.paused) {
                                                    v.play().catch(function(){});
                                                } else {
                                                    clearInterval(tryPlay);
                                                }
                                            }
                                            if (attempts > 15) clearInterval(tryPlay);
                                        }, 250);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        view?.destroy()
                        return true
                    }
                }

                val listener = object : PlayerManager.PlayerCommandListener {
                    override fun onCommandPlay() {
                        post {
                            evaluateJavascript(
                                "(function(){var v=document.querySelector('video');if(v){v.play();}})();",
                                null
                            )
                        }
                    }

                    override fun onCommandPause() {
                        post {
                            evaluateJavascript(
                                "(function(){var v=document.querySelector('video');if(v){v.pause();}})();",
                                null
                            )
                        }
                    }

                    override fun onCommandSeek(seconds: Float) {
                        post {
                            evaluateJavascript(
                                "(function(){var v=document.querySelector('video');if(v){v.currentTime=$seconds;}})();",
                                null
                            )
                        }
                    }

                    override fun onCommandLoadVideo(videoId: String) {
                        post {
                            loadUrl("https://m.youtube.com/watch?v=$videoId")
                        }
                    }

                    override fun onCommandNext() {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var nextBtn = document.querySelector('.ytp-next-button, a.ytp-next-button, [aria-label*="Next video"], [aria-label*="Next"], .next-button, ytm-compact-autoplay-renderer a');
                                    if (nextBtn) { nextBtn.click(); return; }
                                    var recVideo = document.querySelector('ytm-media-item a, ytm-compact-media-item a, ytd-compact-video-renderer a');
                                    if (recVideo) { recVideo.click(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun onCommandPrev() {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v && v.currentTime > 5) { v.currentTime = 0; return; }
                                    var prevBtn = document.querySelector('.ytp-prev-button, a.ytp-prev-button, [aria-label*="Previous video"], [aria-label*="Previous"]');
                                    if (prevBtn) { prevBtn.click(); }
                                    else if (window.history && window.history.length > 1) { window.history.back(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun onResumeTimers() {
                        post {
                            resumeTimers()
                        }
                    }
                }
                PlayerManager.registerBrowseCommandListener(listener)
                webViewInstance = this
            }
        }
    )
}

package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.PlaybackState
import com.example.player.PlayerManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerWebView(
    modifier: Modifier = Modifier,
    initialVideoId: String = "w7k1WqE8jY4"
) {
    val webView = remember {
        var localWebView: WebView? = null
        localWebView
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)

                setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowContentAccess = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject visibility hack to prevent YouTube from auto-pausing on background
                        val jsHack = """
                            Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                            Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
                            document.dispatchEvent(new Event('visibilitychange'));
                        """.trimIndent()
                        view?.evaluateJavascript(jsHack, null)
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        view?.destroy()
                        return true
                    }
                }

                addJavascriptInterface(
                    WebAppInterface(context),
                    "AndroidBridge"
                )

                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    getHtmlForVideo(initialVideoId),
                    "text/html",
                    "UTF-8",
                    null
                )


                // Register listener with PlayerManager
                PlayerManager.registerCommandListener(object : PlayerManager.PlayerCommandListener {
                    override fun onCommandPlay() {
                        post {
                            evaluateJavascript("if (window.player && player.playVideo) { player.playVideo(); }", null)
                        }
                    }

                    override fun onCommandPause() {
                        post {
                            evaluateJavascript("if (window.player && player.pauseVideo) { player.pauseVideo(); }", null)
                        }
                    }

                    override fun onCommandSeek(seconds: Float) {
                        post {
                            evaluateJavascript("if (window.player && player.seekTo) { player.seekTo($seconds, true); }", null)
                        }
                    }

                    override fun onCommandLoadVideo(videoId: String) {
                        post {
                            evaluateJavascript("if (window.player && player.loadVideoById) { player.loadVideoById('$videoId'); } else { window.location.reload(); }", null)
                        }
                    }
                })
            }
        },
        update = { /* Updates handled via PlayerManager listener */ }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Keep background audio active if enabled
        }
    }
}

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun onPlayerReady(duration: Float) {
        PlayerManager.updateFromWebView(PlaybackState.IDLE, 0f, duration)
    }

    @JavascriptInterface
    fun onPlayerStateChange(stateInt: Int, currentTime: Float, duration: Float) {
        val state = when (stateInt) {
            1 -> PlaybackState.PLAYING
            2 -> PlaybackState.PAUSED
            3 -> PlaybackState.BUFFERING
            0 -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }
        PlayerManager.updateFromWebView(state, currentTime, duration)
    }

    @JavascriptInterface
    fun onTimeUpdate(currentTime: Float, duration: Float) {
        PlayerManager.updateFromWebView(PlayerManager.playbackState.value, currentTime, duration)
    }
}

private fun getHtmlForVideo(videoId: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                html, body, #player { width: 100%; height: 100%; overflow: hidden; background-color: #000; }
                iframe { width: 100% !important; height: 100% !important; border: 0; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                // Prevent background pause
                Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });

                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                var progressInterval = null;

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'playsinline': 1,
                            'controls': 1,
                            'rel': 0,
                            'modestbranding': 1,
                            'fs': 1,
                            'enablejsapi': 1
                        },
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange
                        }
                    });
                }

                function onPlayerReady(event) {
                    var duration = player.getDuration() || 0;
                    if (window.AndroidBridge && window.AndroidBridge.onPlayerReady) {
                        window.AndroidBridge.onPlayerReady(duration);
                    }
                    event.target.playVideo();
                }

                function onPlayerStateChange(event) {
                    var currentTime = player.getCurrentTime() || 0;
                    var duration = player.getDuration() || 0;
                    if (window.AndroidBridge && window.AndroidBridge.onPlayerStateChange) {
                        window.AndroidBridge.onPlayerStateChange(event.data, currentTime, duration);
                    }

                    if (event.data === YT.PlayerState.PLAYING) {
                        startProgressTimer();
                    } else {
                        stopProgressTimer();
                    }

                    if (event.data === YT.PlayerState.ENDED) {
                        // Auto loop or next
                    }
                }

                function startProgressTimer() {
                    stopProgressTimer();
                    progressInterval = setInterval(function() {
                        if (player && player.getCurrentTime) {
                            var current = player.getCurrentTime() || 0;
                            var dur = player.getDuration() || 0;
                            if (window.AndroidBridge && window.AndroidBridge.onTimeUpdate) {
                                window.AndroidBridge.onTimeUpdate(current, dur);
                            }
                        }
                    }, 1000);
                }

                function stopProgressTimer() {
                    if (progressInterval) {
                        clearInterval(progressInterval);
                        progressInterval = null;
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

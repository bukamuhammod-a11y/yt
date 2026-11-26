package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.PlaybackState
import com.example.model.YouTubeVideo
import com.example.player.PlayerManager
import java.io.ByteArrayInputStream

/**
 * Custom PersistentBackgroundWebView that prevents the underlying Chromium
 * engine from suspending audio rendering and timers when MainActivity loses focus
 * or when the user locks the screen.
 */
class PersistentBackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (PlayerManager.isBackgroundPlayEnabled.value) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (PlayerManager.isBackgroundPlayEnabled.value) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.dispatchWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (PlayerManager.isBackgroundPlayEnabled.value) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun FullYouTubeWebView(
    onVideoDetected: (hasVideo: Boolean, title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance: WebView? = remember { null }
    val isBgPlay by PlayerManager.isBackgroundPlayEnabled.collectAsState()

    LaunchedEffect(isBgPlay) {
        webViewInstance?.evaluateJavascript(
            "window.__bgPlayActive = ${if (isBgPlay) "true" else "false"};",
            null
        )
    }

    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PersistentBackgroundWebView(context).apply {
                webViewInstance = this
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus(View.FOCUS_DOWN)

                // Ensure keyboard and touch input works seamlessly inside Compose
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                            if (!v.hasFocus()) {
                                v.requestFocus()
                            }
                        }
                    }
                    false
                }

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)

                // Enable hardware acceleration for smooth video rendering and native aspect ratio
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                // 1. Enable Full Cookies & Google Consent Pre-population to prevent bot verification
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                try {
                    cookieManager.setCookie("https://www.youtube.com", "CONSENT=PENDING+987; Path=/")
                    cookieManager.setCookie("https://www.youtube.com", "SOCS=CAESEwgDEgk2NzcxNzM4MDQaAnVzIAEaBgiA_LyaBg; Path=/")
                    cookieManager.setCookie("https://m.youtube.com", "CONSENT=PENDING+987; Path=/")
                    cookieManager.setCookie("https://m.youtube.com", "SOCS=CAESEwgDEgk2NzcxNzM4MDQaAnVzIAEaBgiA_LyaBg; Path=/")
                    cookieManager.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Browser Settings: Native Mobile YouTube Viewport
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowContentAccess = true
                    allowFileAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }

                // 3. Android JavaScript Bridge
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun showKeyboard() {
                            post {
                                requestFocus()
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                            }
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
                                onVideoDetected(onWatch, title, url)
                                val videoId = YouTubeCatalogExtract(url)
                                PlayerManager.updateMainWebViewWatchState(
                                    onWatch = onWatch,
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
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        injectEarlyAdBlockScript(view)
                    }

                    // --- Real YouTube Ad-Blocker at Network Layer ---
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        if (isYouTubeAdRequest(url)) {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        injectAntiBotAndBackgroundScript(view)
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        view?.destroy()
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = view
                        resultMsg?.sendToTarget()
                        return true
                    }
                }

                // Remote Play/Pause commands from AssistiveTouch & Background Notification
                PlayerManager.registerCommandListener(object : PlayerManager.PlayerCommandListener {
                    override fun onCommandPlay() {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) { v.play(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun onCommandPause() {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) { v.pause(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun onCommandSeek(seconds: Float) {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) { v.currentTime = $seconds; }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun onCommandLoadVideo(videoId: String) {
                        post {
                            loadUrl("https://m.youtube.com/watch?v=$videoId")
                        }
                    }

                    override fun onCommandLoadVideoWithTime(videoId: String, startSeconds: Int) {
                        post {
                            val timeParam = if (startSeconds > 2) "&t=${startSeconds}s" else ""
                            loadUrl("https://m.youtube.com/watch?v=$videoId$timeParam")
                        }
                    }

                    override fun onCommandNext() {
                        post {
                            evaluateJavascript(
                                """
                                (function() {
                                    var nextBtn = document.querySelector('.ytp-next-button, a.ytp-next-button, [aria-label*="Next video"], [aria-label*="Next"], .next-button, ytm-compact-autoplay-renderer a');
                                    if (nextBtn) {
                                        nextBtn.click();
                                        return;
                                    }
                                    var recVideo = document.querySelector('ytm-media-item a, ytm-compact-media-item a, ytd-compact-video-renderer a, #contents ytd-compact-video-renderer a');
                                    if (recVideo) {
                                        recVideo.click();
                                        return;
                                    }
                                    try {
                                        var evt = new KeyboardEvent('keydown', { key: 'N', code: 'KeyN', shiftKey: true, keyCode: 78, which: 78, bubbles: true });
                                        document.dispatchEvent(evt);
                                    } catch(e) {}
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
                                    if (v && v.currentTime > 5) {
                                        v.currentTime = 0;
                                        return;
                                    }
                                    var prevBtn = document.querySelector('.ytp-prev-button, a.ytp-prev-button, [aria-label*="Previous video"], [aria-label*="Previous"]');
                                    if (prevBtn) {
                                        prevBtn.click();
                                    } else if (window.history && window.history.length > 1) {
                                        window.history.back();
                                    }
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
                })

                loadUrl("https://m.youtube.com")
            }
        },
        update = { webView ->
            webViewInstance = webView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Keep background play going
        }
    }
}

internal fun isYouTubeAdRequest(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("doubleclick.net") ||
        lower.contains("googleads.g.doubleclick.net") ||
        lower.contains("googlesyndication.com") ||
        lower.contains("adservice.google") ||
        lower.contains("googleadservices.com") ||
        lower.contains("youtube.com/pagead/") ||
        lower.contains("youtube.com/pcs/activeview") ||
        lower.contains("youtube.com/api/stats/ads") ||
        lower.contains("youtube.com/ptracking") ||
        lower.contains("get_midroll_info") ||
        lower.contains("get_ad_tags") ||
        lower.contains("youtubei/v1/player/ad_break") ||
        lower.contains("youtubei/v1/att/")
}

internal fun injectEarlyAdBlockScript(webView: WebView?) {
    val js = """
        (function() {
            if (window.__ytEarlyAdBlockInjected) return;
            window.__ytEarlyAdBlockInjected = true;

            function sanitizeObj(obj) {
                if (!obj || typeof obj !== 'object') return obj;
                var adKeys = [
                    'adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams',
                    'adSignalsInfo', 'masthead', 'promotedSparklesTextSearchRenderer',
                    'inFeedAdLayoutRenderer', 'adSlotRenderer', 'companionAdRenderer'
                ];
                for (var i = 0; i < adKeys.length; i++) {
                    if (adKeys[i] in obj) {
                        try { delete obj[adKeys[i]]; } catch(e) {}
                    }
                }
                if (obj.playerResponse) {
                    sanitizeObj(obj.playerResponse);
                }
                return obj;
            }

            // 1. Intercept JSON.parse
            try {
                var origParse = JSON.parse;
                JSON.parse = function(text, reviver) {
                    var res = origParse.apply(this, arguments);
                    if (res && typeof res === 'object') {
                        sanitizeObj(res);
                    }
                    return res;
                };
            } catch(e) {}

            // 2. Intercept window.ytInitialPlayerResponse
            try {
                var _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
                Object.defineProperty(window, 'ytInitialPlayerResponse', {
                    get: function() { return _ytInitialPlayerResponse; },
                    set: function(val) {
                        if (val && typeof val === 'object') { sanitizeObj(val); }
                        _ytInitialPlayerResponse = val;
                    },
                    configurable: true
                });
            } catch(e) {}

            // 3. Intercept fetch
            try {
                if (window.fetch) {
                    var origFetch = window.fetch;
                    window.fetch = function() {
                        var args = arguments;
                        var url = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url ? args[0].url : '');
                        
                        if (url && (
                            url.indexOf('/pagead/') !== -1 ||
                            url.indexOf('doubleclick.net') !== -1 ||
                            url.indexOf('googleads') !== -1 ||
                            url.indexOf('/api/stats/ads') !== -1 ||
                            url.indexOf('/ptracking') !== -1 ||
                            url.indexOf('get_midroll_info') !== -1 ||
                            url.indexOf('get_ad_tags') !== -1
                        )) {
                            return Promise.resolve(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
                        }

                        return origFetch.apply(this, args).then(function(response) {
                            if (url && (url.indexOf('/youtubei/v1/player') !== -1 || url.indexOf('/youtubei/v1/next') !== -1)) {
                                var cloned = response.clone();
                                return cloned.json().then(function(json) {
                                    sanitizeObj(json);
                                    return new Response(JSON.stringify(json), {
                                        status: response.status,
                                        statusText: response.statusText,
                                        headers: response.headers
                                    });
                                }).catch(function() {
                                    return response;
                                });
                            }
                            return response;
                        });
                    };
                }
            } catch(e) {}

            // 4. Intercept XMLHttpRequest
            try {
                if (window.XMLHttpRequest) {
                    var origOpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        this._url = url;
                        return origOpen.apply(this, arguments);
                    };

                    var origSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.send = function(data) {
                        if (this._url && (
                            this._url.indexOf('/pagead/') !== -1 ||
                            this._url.indexOf('doubleclick.net') !== -1 ||
                            this._url.indexOf('googleads') !== -1 ||
                            this._url.indexOf('/api/stats/ads') !== -1 ||
                            this._url.indexOf('get_midroll_info') !== -1 ||
                            this._url.indexOf('get_ad_tags') !== -1
                        )) {
                            try { this.abort(); } catch(e) {}
                            return;
                        }
                        return origSend.apply(this, arguments);
                    };
                }
            } catch(e) {}
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

internal fun injectAntiBotAndBackgroundScript(webView: WebView?) {
    injectEarlyAdBlockScript(webView)
    val js = """
        (function() {
            // 1. YouTube Native Dark Theme & Ultra Ad-Blocker CSS
            try {
                if (!document.getElementById('native-yt-styling')) {
                    var style = document.createElement('style');
                    style.id = 'native-yt-styling';
                    style.innerHTML = `
                        /* Remove YouTube Ads, Banners & Promos */
                        .ad-container, .ad-interrupting, ytd-promoted-sparkles-web-renderer, 
                        .ytp-ad-overlay-container, #player-ads, ytm-promoted-sparkles-text-search-renderer, 
                        .sparkles-light-cta, ytm-companion-ad-renderer, .ytm-promoted-sparkles-web-renderer,
                        ytm-promoted-video-renderer, .ytp-ad-message-container, .ytp-ad-action-interstitial,
                        .ytm-ad-badge, .ytp-ad-module, ytm-ad-slot-renderer,
                        ytm-pivot-bar-renderer .pivot-w2w, .upsell-dialog-renderer, ytm-app-banner,
                        .mobile-topbar-header .promoted-sparkles-web-banner, 
                        .yt-spec-bottom-sheet-layout__bottom-sheet-renderer-container,
                        ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ytd-banner-promo-renderer,
                        ytd-statement-banner-renderer, .video-ads, .ytp-ad-progress-bar-container,
                        #masthead-ad, ytm-inline-ad-renderer, .ad-showing .ytp-chrome-controls,
                        .ytp-ad-image-overlay, .ytp-ad-overlay-slot, [class*="ad-slot"], [class*="ad-showing"],
                        ytm-promoted-item-renderer, ytd-action-companion-ad-renderer, .ytp-ad-text {
                            display: none !important;
                            visibility: hidden !important;
                            height: 0 !important;
                            max-height: 0 !important;
                            opacity: 0 !important;
                            pointer-events: none !important;
                        }

                        /* YouTube App Dark Canvas */
                        body, html {
                            background-color: #0f0f0f !important;
                            color: #f1f1f1 !important;
                            font-family: Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
                            -webkit-tap-highlight-color: transparent !important;
                            overflow-x: hidden !important;
                        }

                        /* Rounded Thumbnails */
                        ytm-media-item .media-item-thumbnail-container, 
                        .video-thumbnail-container-compact, 
                        .compact-media-item-image {
                            border-radius: 12px !important;
                            overflow: hidden !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
            } catch(e) {}

            // 2. Anti-Bot: Mask navigator webdriver & emulate Chrome browser features
            try {
                Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true });
                delete navigator.__proto__.webdriver;
            } catch(e) {}

            try {
                window.chrome = window.chrome || {
                    app: { isInstalled: false },
                    runtime: { OnInstalledReason: {} }
                };
            } catch(e) {}

            try {
                Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
                Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en', 'bn'] });
            } catch(e) {}

            // 3. Keep-Alive in Background: Fake Document Visibility API
            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });

                ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze'].forEach(function(evt) {
                    window.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
                    document.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
                });
            } catch(e) {}

            // 4. Soft Keyboard Trigger when tapping Search Bar or Input Elements
            try {
                document.addEventListener('focusin', function(e) {
                    if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.getAttribute('contenteditable') === 'true')) {
                        if (window.AndroidBridge && window.AndroidBridge.showKeyboard) {
                            window.AndroidBridge.showKeyboard();
                        }
                    }
                }, true);

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    while (target && target !== document.body) {
                        if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.getAttribute('role') === 'searchbox' || (target.className && typeof target.className === 'string' && target.className.indexOf('search') !== -1)) {
                            if (window.AndroidBridge && window.AndroidBridge.showKeyboard) {
                                window.AndroidBridge.showKeyboard();
                            }
                            break;
                        }
                        target = target.parentElement;
                    }
                }, true);
            } catch(e) {}

            // 5. Ultra-Fast Ad-Skipper & Status Reporter (Runs every 200ms)
            if (!window.__ytAndroidBridgeAttached) {
                window.__ytAndroidBridgeAttached = true;
                setInterval(function() {
                    var url = window.location.href;
                    var isWatch = url.indexOf('/watch') !== -1 || url.indexOf('/shorts/') !== -1;
                    var video = document.querySelector('video');
                    var isPlaying = (isWatch && video) ? (!video.paused && !video.ended) : false;
                    var pos = (isWatch && video && !isNaN(video.currentTime)) ? video.currentTime : 0;
                    var dur = (isWatch && video && !isNaN(video.duration) && isFinite(video.duration)) ? video.duration : 0;

                    // --- Ultra-Fast Video Ad Skipper & Fallback Cleaner ---
                    try {
                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot button, .videoAdUiSkipButton, [id^="skip-button"] button, .ytp-ad-skip-button-container button');
                        if (skipBtn) {
                            skipBtn.click();
                        }
                        var isAd = document.querySelector('#movie_player.ad-showing, .html5-video-player.ad-showing, .ad-interrupting, .ad-showing') !== null;
                        if (isAd && video) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (video.duration && !isNaN(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration - 0.01;
                            }
                        }
                    } catch(e) {}

                    var title = document.title || 'YouTube';
                    var titleEl = document.querySelector('.slim-video-metadata-title, h1.title, .video-details h2');
                    if (titleEl && titleEl.innerText) {
                        title = titleEl.innerText;
                    }

                    var channel = 'YouTube';
                    var chEl = document.querySelector('ytm-slim-owner-renderer .slim-owner-channel-name, .slim-owner-icon-and-title .yt-core-attributed-string, #owner-name, ytm-slim-owner-renderer h3');
                    if (chEl && chEl.innerText) {
                        channel = chEl.innerText;
                    }

                    if (window.AndroidBridge && window.AndroidBridge.onVideoStatus) {
                        window.AndroidBridge.onVideoStatus(isWatch, isPlaying, pos, dur, title, channel, url);
                    }
                }, 200);
            }
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

internal fun YouTubeCatalogExtract(url: String): String {
    return try {
        if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&")
        } else if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        } else if (url.contains("/shorts/")) {
            url.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}


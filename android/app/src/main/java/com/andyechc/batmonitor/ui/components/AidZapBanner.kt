package com.andyechc.batmonitor.ui.components

import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val AD_HTML = """
    <!doctype html>
    <html lang="es">
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
      <style>
        html, body {
          margin: 0;
          padding: 0;
          background: #171717;
          overflow: hidden;
        }
        body {
          display: flex;
          justify-content: center;
          align-items: center;
          height: 58px;
        }
        a {
          display: block;
          width: min(728px, 100vw);
          line-height: 0;
        }
        img {
          display: block;
          width: 100%;
          height: 58px;
          max-width: 728px;
          object-fit: contain;
          border: 0;
        }
      </style>
    </head>
    <body>
      <a href="https://aidzap.com/r/B3425FB3" target="_blank">
        <img src="https://aidzap.com/banner-img/publisher/en/aidzap-publisher-en-728x90" width="728" height="90" alt="AidZap">
      </a>
    </body>
    </html>
"""

/** Fixed AidZap banner. The WebView is a static display area with no scrolling or zoom. */
@Composable
fun AidZapBanner(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { factoryContext ->
            WebView(factoryContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (!url.startsWith("http://") && !url.startsWith("https://")) return true
                        return try {
                            view.context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url),
                                ).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                },
                            )
                            true
                        } catch (_: Exception) {
                            true
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    loadsImagesAutomatically = true
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mediaPlaybackRequiresUserGesture = true
                    displayZoomControls = false
                    builtInZoomControls = false
                    setSupportZoom(false)
                    setSupportMultipleWindows(false)
                    setNeedInitialFocus(false)
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isLongClickable = false
                setOnTouchListener { _, event ->
                    event.actionMasked == MotionEvent.ACTION_MOVE
                }
                post {
                    loadDataWithBaseURL("https://aidzap.com/", AD_HTML, "text/html", "UTF-8", null)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 728.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp)),
        onRelease = WebView::destroy,
    )
}

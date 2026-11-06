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
          align-items: flex-start;
          justify-content: center;
          height: 60px;
        }
        a {
          display: block;
          line-height: 0;
        }
        img {
          display: block;
          width: min(468px, 100vw);
          height: 60px;
          max-width: 100vw;
          object-fit: contain;
        }
      </style>
    </head>
    <body>
      <a href="https://bitmedia.io?r=mw4q968c">
        <img src="https://bitmedia.io/app/react/pb.w468h60.gif" alt="">
      </a>
    </body>
    </html>
"""

/** Fixed BitMedia banner. The WebView is a static 468 x 60 display area with no scrolling or zoom. */
@Composable
fun BitMediaBanner(modifier: Modifier = Modifier) {
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
                    loadDataWithBaseURL("https://bitmedia.io/", AD_HTML, "text/html", "UTF-8", null)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp)),
        onRelease = WebView::destroy,
    )
}

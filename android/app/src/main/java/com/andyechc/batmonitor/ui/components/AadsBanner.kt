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
          background: #080808;
          overflow: hidden;
        }
        body {
          display: flex;
          align-items: flex-start;
          justify-content: center;
          height: 90px;
        }
        a {
          display: block;
          line-height: 0;
        }
        iframe {
          display: block;
          width: 70%;
          height: 90px;
          border: 0;
          margin: 0 auto;
          overflow: hidden;
        }
        .label {
          position: absolute;
          left: 15%;
          top: -24px;
          z-index: 2;
          font-size: 13px;
          color: #263238;
          background: #F8F8F9;
          padding: 4px 10px;
          border-radius: 4px 4px 0 0;
          text-decoration: none;
        }
        .wrap {
          position: relative;
          width: 100%;
          height: 90px;
        }
      </style>
    </head><body>
      <div class="wrap">
        <a class="label" href="https://aads.com/campaigns/new?source_id=2461253&source_type=ad_unit&partner=2461253" target="_blank">
          Advertise here
        </a>
        <iframe data-aa="2461253" src="//acceptable.a-ads.com/2461253/?size=Adaptive&background_color=080808&title_color=2EE6A6&title_hover_color=24b280&text_color=FFFFFF&link_color=2EE6A6&link_hover_color=24b280"></iframe>
      </div>
    </body>
    </html>
"""

/** Fixed AADS banner. The WebView is rendered once with scrolling and zoom disabled. */
@Composable
fun AadsBanner(modifier: Modifier = Modifier) {
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
                    loadDataWithBaseURL(
                        "https://acceptable.a-ads.com/",
                        AD_HTML,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp)),
        onRelease = WebView::destroy,
    )
}

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

private const val NATIVE_AD_HTML = """
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
        .native-ads {
          width: min(728px, 100vw);
          min-height: 58px;
        }
      </style>
    </head>
    <body>
      <div class="native-ads" data-aidzap-unit="f9bd1068-163e-47a8-8390-54d1dc0c5803" data-aidzap-type="native"></div>
      <!-- AidZap native loader: la red inyecta el contenido del unit. -->
      <script>
        (function () {
          document.querySelectorAll('.native-ads[data-aidzap-unit]').forEach(function (el) {
            el.setAttribute('data-aidzap-loaded', 'pending');
          });
        })();
      </script>
    </body>
    </html>
"""

/** Fixed AidZap native banner. The WebView is a static display area with no scrolling or zoom. */
@Composable
fun AidZapNativeBanner(modifier: Modifier = Modifier) {
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
                    loadDataWithBaseURL("https://aidzap.com/", NATIVE_AD_HTML, "text/html", "UTF-8", null)
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

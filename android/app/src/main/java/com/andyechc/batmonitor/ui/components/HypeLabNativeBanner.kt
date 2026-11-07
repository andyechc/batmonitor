package com.andyechc.batmonitor.ui.components

import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
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
          min-height: 110px;
        }
        hype-native {
          display: block;
          width: min(728px, 100vw);
        }
        .native-ad {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 10px 12px;
          color: #f2f2f2;
          font-family: -apple-system, Roboto, sans-serif;
          text-decoration: none;
        }
        .native-ad__icon {
          width: 48px;
          height: 48px;
          object-fit: cover;
          border-radius: 8px;
          flex: 0 0 auto;
        }
        .native-ad__advertiser {
          font-size: 10px;
          letter-spacing: .06em;
          text-transform: uppercase;
          color: #8e8e93;
        }
        .native-ad__headline {
          font-size: 14px;
          font-weight: 600;
          margin-top: 2px;
        }
        .native-ad__body {
          font-size: 12px;
          color: #c7c7cc;
          margin-top: 2px;
        }
        .native-ad__media, .native-ad__cta { display: none; }
        a { text-decoration: none; color: inherit; }
      </style>
      <script defer src="https://api.hypelab.com/v1/scripts/hp-sdk.js?v=0"></script>
      <script>
        document.addEventListener("DOMContentLoaded", function () {
          window.HypeLab && HypeLab.initialize({
            environment: "production",
            propertySlug: "9af16221b7",
          });
        });
      </script>
    </head>
    <body>
      <hype-native placement="4f3cc7a8a2">
        <div class="native-ad">
          <img data-ref="icon" class="native-ad__icon" alt="" />
          <div>
            <div data-ref="advertiser" class="native-ad__advertiser"></div>
            <div data-ref="headline" class="native-ad__headline"></div>
            <div data-ref="body" class="native-ad__body"></div>
          </div>
          <a data-ref="ctaLink" href="/" target="_blank">
            <div data-ref="mediaContent" class="native-ad__media"></div>
            <div data-ref="ctaText" class="native-ad__cta"></div>
          </a>
        </div>
      </hype-native>
    </body>
    </html>
"""

/** Fixed HypeLab native banner (detail Summary). Static WebView, no scrolling or zoom. */
@Composable
fun HypeLabNativeBanner(modifier: Modifier = Modifier) {
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
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mediaPlaybackRequiresUserGesture = true
                    displayZoomControls = false
                    builtInZoomControls = false
                    setSupportZoom(false)
                    setSupportMultipleWindows(false)
                    setNeedInitialFocus(false)
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isLongClickable = false
                setOnTouchListener { _, event ->
                    event.actionMasked == MotionEvent.ACTION_MOVE
                }
                post {
                    loadDataWithBaseURL("https://api.hypelab.com/", NATIVE_AD_HTML, "text/html", "UTF-8", null)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 728.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp)),
        onRelease = WebView::destroy,
    )
}

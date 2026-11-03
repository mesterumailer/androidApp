package com.mesterumailer.smsmanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class HomeAd(
    val slug: String,
    val title: String,
    val description: String,
    val ctaText: String,
    val imageUrl: String,
    val clickUrl: String
)

/**
 * Loads a single active home-bottom ad from Cloudflare Worker.
 *
 * The request contains no SMS, sender, contact, account, or device identifier.
 * The last valid ad is cached locally so the banner can still be shown offline.
 */
object HomeAdService {
    private const val API_URL =
        "https://sms-manager-ads.mesterumailer.workers.dev/api/v1/ads/active?placement=home_bottom"
    private const val PREFS_NAME = "remote_ad_cache"
    private const val CACHE_KEY = "home_bottom_ad_json"
    private const val MAX_IMAGE_BYTES = 600_000

    fun load(context: Context): Pair<HomeAd?, Bitmap?> {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val remote = fetchRemoteAd()
        val cachedJson = preferences.getString(CACHE_KEY, null)
        val selectedJson = when {
            remote.requestSucceeded && remote.adJson == null -> {
                preferences.edit().remove(CACHE_KEY).apply()
                null
            }
            remote.requestSucceeded -> {
                preferences.edit().putString(CACHE_KEY, remote.adJson).apply()
                remote.adJson
            }
            else -> cachedJson
        } ?: return null to null

        val ad = parseAd(selectedJson) ?: return null to null
        val bitmap = ad.imageUrl.takeIf(::isHttpsUrl)?.let(::downloadImage)
        return ad to bitmap
    }

    private data class RemoteResult(
        val requestSucceeded: Boolean,
        val adJson: String?
    )

    private fun fetchRemoteAd(): RemoteResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return RemoteResult(false, null)
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(payload)
            if (!root.has("ad")) return RemoteResult(false, null)
            val ad = root.optJSONObject("ad")
            RemoteResult(true, ad?.toString())
        } catch (_: Exception) {
            RemoteResult(false, null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAd(rawJson: String): HomeAd? {
        return try {
            val json = JSONObject(rawJson)
            val slug = json.optString("slug").trim()
            val title = json.optString("title").trim()
            val clickUrl = json.optString("click_url").trim()
            if (slug.isBlank() || title.isBlank() || !isHttpsUrl(clickUrl)) {
                null
            } else {
                HomeAd(
                    slug = slug,
                    title = title,
                    description = json.optString("description", "").trim(),
                    ctaText = json.optString("cta_text", "مشاهده").trim().ifBlank { "مشاهده" },
                    imageUrl = json.optString("image_url", "").trim(),
                    clickUrl = clickUrl
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isHttpsUrl(value: String): Boolean {
        if (value.isBlank()) return false
        val uri = Uri.parse(value)
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    private fun downloadImage(imageUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 5_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "image/webp,image/*")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            if (contentLength > MAX_IMAGE_BYTES) return null

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

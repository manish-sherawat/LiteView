package com.nexus.core.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val version: String, val releaseNotes: String, val downloadUrl: String) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
    object Downloading : UpdateState()
}

@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // IMPORTANT: Change these to your actual GitHub username and repository name!
    private val githubOwner = "manish-sherawat"
    private val githubRepo = "LiteView"
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    suspend fun checkForUpdates(currentVersionName: String) {
        _updateState.value = UpdateState.Checking
        try {
            withContext(Dispatchers.IO) {
                val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val latestVersion = tagName.removePrefix("v").removePrefix("V")
                    val body = json.optString("body", "No release notes available.")
                    
                    val assets = json.getJSONArray("assets")
                    var downloadUrl: String? = null
                    
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    
                    if (downloadUrl != null) {
                        if (isNewerVersion(latestVersion, currentVersionName)) {
                            _updateState.value = UpdateState.Available(latestVersion, body, downloadUrl)
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = UpdateState.Error("No APK found in the latest release.")
                    }
                } else if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    _updateState.value = UpdateState.Error("Repository or release not found. Please check your GitHub username and repository name in AppUpdater.kt.")
                } else {
                    _updateState.value = UpdateState.Error("Failed to check for updates. Code: ${connection.responseCode}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Unknown error occurred.")
        }
    }
    
    fun downloadAndInstallUpdate(url: String, version: String) {
        _updateState.value = UpdateState.Downloading
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            val fileName = "LiteView_update_$version.apk"
            
            val request = DownloadManager.Request(uri)
                .setTitle("LiteView Update")
                .setDescription("Downloading version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
            
            downloadManager.enqueue(request)
            
            // The user will tap the completed download notification to install the app.
            
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error("Failed to start download: ${e.localizedMessage}")
        }
    }
    
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    suspend fun fetchAllReleases(): List<GithubRelease> {
        return withContext(Dispatchers.IO) {
            val releases = mutableListOf<GithubRelease>()
            try {
                val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/releases")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val tagName = json.getString("tag_name")
                        val dateString = json.getString("published_at")
                        val body = json.optString("body", "No release notes.")
                        
                        // Parse date string (e.g. "2026-06-06T12:00:00Z") to "June 2026"
                        val formattedDate = try {
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            val date = formatter.parse(dateString)
                            if (date != null) {
                                val outFormatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US)
                                outFormatter.format(date)
                            } else {
                                dateString.substringBefore("T")
                            }
                        } catch (e: Exception) {
                            dateString.substringBefore("T")
                        }
                        
                        releases.add(GithubRelease(tagName, formattedDate, body))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            releases
        }
    }
}

data class GithubRelease(val version: String, val date: String, val body: String)

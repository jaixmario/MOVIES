package com.mario.movies.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStreamReader

data class TokenData(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val expire_at: Long? // Timestamp when it expires
)

data class Links(
    val downloadLink: String?,
    val shareLink: String?
)

class TokenManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    companion object {
        private const val CLIENT_ID = "59790544-ca0c-4b77-b338-26ff9d1b676f"
        private const val TENANT_ID = "0fd666e8-0b3d-41ea-a5ef-1c509130bd94"
        private const val GRAPH_ROOT = "https://graph.microsoft.com/v1.0"
        private const val TOKEN_FILE = "user_token.json"
    }
    
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val tokenData = loadToken() ?: return@withContext null
        
        // Check if expired (or expiring soon, e.g., within 5 minutes)
        val currentTime = System.currentTimeMillis() / 1000
        val expiryTime = tokenData.expire_at ?: (currentTime + tokenData.expires_in)
        
        if (currentTime > expiryTime - 300) {
            // Expired, refresh it
            return@withContext refreshToken(tokenData.refresh_token)
        }
        
        return@withContext tokenData.access_token
    }
    
    private fun loadToken(): TokenData? {
        val file = File(context.filesDir, TOKEN_FILE)
        if (file.exists()) {
             return gson.fromJson(file.readText(), TokenData::class.java)
        }
        
        // Fallback to assets if not in filesDir yet
        try {
            val inputStream = context.assets.open(TOKEN_FILE)
            val reader = InputStreamReader(inputStream)
            val tokenData = gson.fromJson(reader, TokenData::class.java)
            // Save to filesDir with expiry timestamp if not present
            val currentTime = System.currentTimeMillis() / 1000
            val updatedToken = if (tokenData.expire_at == null) {
                tokenData.copy(expire_at = currentTime + tokenData.expires_in)
            } else tokenData
            
            saveToken(updatedToken)
            return updatedToken
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun saveToken(tokenData: TokenData) {
        val file = File(context.filesDir, TOKEN_FILE)
        file.writeText(gson.toJson(tokenData))
    }
    
    private fun refreshToken(refreshToken: String): String? {
        val url = "https://login.microsoftonline.com/$TENANT_ID/oauth2/v2.0/token"
        
        val formBody = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "https://graph.microsoft.com/.default")
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
            
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                val newToken = gson.fromJson(json, TokenData::class.java)
                
                val currentTime = System.currentTimeMillis() / 1000
                val updatedToken = newToken.copy(expire_at = currentTime + newToken.expires_in)
                
                saveToken(updatedToken)
                return updatedToken.access_token
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    suspend fun getLinks(itemId: String): Links? = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext null
        
        var downloadLink: String? = null
        var shareLink: String? = null
        
        // 1. Get Download URL (GET item)
        try {
            val url = "$GRAPH_ROOT/me/drive/items/$itemId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                val responseObj = gson.fromJson(json, JsonObject::class.java)
                if (responseObj.has("@microsoft.graph.downloadUrl")) {
                    downloadLink = responseObj.get("@microsoft.graph.downloadUrl").asString
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 2. Create Share Link (POST createLink)
        try {
            val url = "$GRAPH_ROOT/me/drive/items/$itemId/createLink"
            val jsonBody = """
                {
                    "type": "view",
                    "scope": "anonymous"
                }
            """.trimIndent()
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
                
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                val responseObj = gson.fromJson(json, JsonObject::class.java)
                if (responseObj.has("link")) {
                    val linkObj = responseObj.getAsJsonObject("link")
                    if (linkObj.has("webUrl")) {
                        shareLink = linkObj.get("webUrl").asString
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (downloadLink != null || shareLink != null) {
            return@withContext Links(downloadLink, shareLink)
        }
        return@withContext null
    }
}

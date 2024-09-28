// src/main/kotlin/org/datastic/docsbuilder/ChatGPTService.kt

package org.datastic.docsbuilder

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.math.pow

object ChatGPTService {
    private const val COMPLETIONS_API_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODELS_API_URL = "https://api.openai.com/v1/models"
    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    // Configure Logging Interceptor
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Set to NONE in production
    }

    // Configure Retry Interceptor with Exponential Backoff
    private val retryInterceptor = object : okhttp3.Interceptor {
        private val maxRetries = 3
        private val baseDelay = 1000L // 1 second

        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            var attempt = 0
            var request = chain.request()
            var response: okhttp3.Response

            while (true) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return response
                    }
                } catch (e: Exception) {
                    if (attempt >= maxRetries) {
                        throw e
                    }
                }
                attempt++
                val delay = baseDelay * 2.0.pow(attempt.toDouble()).toLong()
                Thread.sleep(delay)
            }
        }
    }

    // Configure OkHttpClient with Interceptors and Timeouts
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(retryInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the list of available models from the OpenAI API.
     * Returns a list of model IDs.
     */
    fun fetchAvailableModels(apiKey: String): List<String> {
        val request = Request.Builder()
            .url(MODELS_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return emptyList()
                }

                val responseBody = response.body?.string() ?: return emptyList()
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val dataArray: JsonArray = jsonResponse.getAsJsonArray("data")
                dataArray.map { it.asJsonObject.get("id").asString }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Generates documentation for the given file content using the selected model.
     * Returns the generated documentation as a String.
     */
    fun generateDocumentation(fileContent: String): String {
        if (fileContent.length > 100000) { // Example file size limit
            return "File is too large to generate documentation. Please split the file and try again."
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return "API Key is missing. Please configure your OpenAI API key."
        }

        val selectedModel = PluginSettings.instance.state.selectedModel
        val prompt = fileContent.trim()

        val json = JsonObject().apply {
            addProperty("model", selectedModel) // Use selected model
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to prompt))))
            addProperty("max_tokens", PluginSettings.instance.state.maxTokens) // Adjust as needed
            addProperty("temperature", PluginSettings.instance.state.temperature) // Optional: Control randomness
        }

        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(COMPLETIONS_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        return "Unauthorized: Invalid API Key. Please check your API key in the settings."
                    }
                    return "API request failed with code: ${response.code} and message: ${response.message}"
                }

                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    val choices = jsonResponse.getAsJsonArray("choices")
                    if (choices.size() > 0) {
                        val content = choices.get(0)
                            .asJsonObject
                            .getAsJsonObject("message")
                            .get("content")
                            .asString
                        content.trim()
                    } else {
                        "No documentation generated."
                    }
                } else {
                    "Empty response from API."
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            "Request timed out. Please check your internet connection and try again."
        } catch (e: Exception) {
            "An error occurred: ${e.message}"
        }
    }

    /**
     * Retrieves the API key from plugin settings.
     */
    private fun getApiKey(): String {
        return PluginSettings.instance.state.apiKey
    }
}
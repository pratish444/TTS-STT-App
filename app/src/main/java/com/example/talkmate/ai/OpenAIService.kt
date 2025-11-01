package com.example.talkmate.ai

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data classes for OpenAI API
data class OpenAIRequest(
    @SerializedName("model") val model: String = "gpt-3.5-turbo",
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 1000,
    @SerializedName("temperature") val temperature: Double = 0.7
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class OpenAIResponse(
    @SerializedName("choices") val choices: List<Choice>
)

data class Choice(
    @SerializedName("message") val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

// Retrofit interface for OpenAI API
interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenAIRequest
    ): Response<OpenAIResponse>
}

class OpenAIService {

    // IMPORTANT: Replace with your actual OpenAI API key
    // Get it from: https://platform.openai.com/api-keys
    private val apiKey = "YOUR_OPENAI_API_KEY_HERE"

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(OpenAIApi::class.java)

    suspend fun getIntelligentResponse(userQuestion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Check if API key is set
                if (apiKey == "YOUR_OPENAI_API_KEY_HERE") {
                    return@withContext "I need an OpenAI API key to give intelligent responses. Please set your API key in OpenAIService.kt"
                }

                val messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "You are a helpful voice assistant. Give concise, accurate, and friendly responses. Keep answers under 100 words unless more detail is specifically requested."
                    ),
                    ChatMessage(
                        role = "user",
                        content = userQuestion
                    )
                )

                val request = OpenAIRequest(
                    model = "gpt-3.5-turbo",
                    messages = messages,
                    maxTokens = 150,
                    temperature = 0.7
                )

                val response = api.chatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )

                if (response.isSuccessful) {
                    val aiResponse = response.body()
                    aiResponse?.choices?.firstOrNull()?.message?.content?.trim()
                        ?: "I couldn't generate a response right now."
                } else {
                    when (response.code()) {
                        401 -> "Invalid API key. Please check your OpenAI API key."
                        429 -> "Too many requests. Please try again in a moment."
                        500 -> "OpenAI service is temporarily unavailable."
                        else -> "Sorry, I couldn't connect to the AI service right now."
                    }
                }
            } catch (e: Exception) {
                "Sorry, I'm having trouble connecting to the AI service: ${e.message}"
            }
        }
    }

    suspend fun processImageText(extractedText: String): String {
        val prompt =
            "I scanned this text from an image: '$extractedText'. Please read it back to me and tell me what it's about or if you can help with any questions related to this text."
        return getIntelligentResponse(prompt)
    }
}

// Alternative: Google Gemini API Service (Free tier available)
class GeminiService {

    // Get your free API key from: https://makersuite.google.com/app/apikey
    private val apiKey = "YOUR_GEMINI_API_KEY_HERE"

    suspend fun getIntelligentResponse(userQuestion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey == "YOUR_GEMINI_API_KEY_HERE") {
                    return@withContext "Please set your Google Gemini API key for intelligent responses."
                }

                // For now, return a placeholder - you can implement Gemini API calls here
                // Gemini has a different API structure than OpenAI
                "Gemini integration coming soon. Please use OpenAI for now."

            } catch (e: Exception) {
                "Error connecting to Gemini API: ${e.message}"
            }
        }
    }
}
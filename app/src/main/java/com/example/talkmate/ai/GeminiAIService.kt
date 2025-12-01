package com.example.talkmate.ai

import android.util.Log
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent,
    @SerializedName("finishReason") val finishReason: String?
)

// Retrofit interface for Gemini API
interface GeminiApi {
    // Use v1beta with the correct model name
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

class GeminiAIService {

    private val apiKey = "API KEY"

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeminiApi::class.java)

    suspend fun getIntelligentResponse(userQuestion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("GeminiAI", "Processing question: $userQuestion")
                Log.d("GeminiAI", "API Key length: ${apiKey.length}")

                if (apiKey == "YOUR_API_KEY_HERE" || apiKey.isBlank()) {
                    Log.e("GeminiAI", "API key not set!")
                    return@withContext getSmartLocalResponse(userQuestion)
                }

                val prompt = """
                You are a helpful voice assistant. Answer the user's question concisely and accurately. 
                Keep your response under 50 words unless more detail is specifically requested. 
                Be friendly and conversational.
                
                User question: $userQuestion
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = prompt))
                        )
                    )
                )

                Log.d("GeminiAI", "Making API call to gemini-1.5-flash...")
                val response = api.generateContent(apiKey, request)

                Log.d("GeminiAI", "Response code: ${response.code()}")
                Log.d("GeminiAI", "Response message: ${response.message()}")

                if (response.isSuccessful) {
                    val geminiResponse = response.body()
                    val responseText = geminiResponse?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text

                    if (responseText != null) {
                        Log.d("GeminiAI", "Success! Response: $responseText")
                        return@withContext responseText.trim()
                    } else {
                        Log.e("GeminiAI", "Response body was null or empty")
                        return@withContext getSmartLocalResponse(userQuestion)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("GeminiAI", "API Error: ${response.code()} - $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> {
                            Log.e("GeminiAI", "Bad request - check API key format")
                            "API key might be invalid. Using local responses."
                        }
                        401 -> {
                            Log.e("GeminiAI", "Unauthorized - API key is wrong")
                            "API key is incorrect. Please check your key."
                        }
                        403 -> {
                            Log.e("GeminiAI", "Forbidden - API key doesn't have permission")
                            "API key doesn't have permission. Check your Google Cloud settings."
                        }
                        404 -> {
                            Log.e("GeminiAI", "Model not found - API structure changed")
                            "Model endpoint issue. Using local responses."
                        }
                        429 -> {
                            Log.e("GeminiAI", "Rate limit exceeded")
                            "Too many requests. Please try again in a moment."
                        }
                        else -> {
                            Log.e("GeminiAI", "Unknown error: ${response.code()}")
                            "Network issue. Using local responses."
                        }
                    }

                    if (response.code() == 401 || response.code() == 403) {
                        return@withContext errorMessage
                    }

                    return@withContext getSmartLocalResponse(userQuestion)
                }
            } catch (e: Exception) {
                Log.e("GeminiAI", "Exception: ${e.message}", e)
                Log.e("GeminiAI", "Stack trace: ${e.stackTraceToString()}")

                return@withContext getSmartLocalResponse(userQuestion)
            }
        }
    }

    private fun getSmartLocalResponse(question: String): String {
        val q = question.lowercase().trim()

        Log.d("GeminiAI", "Using local response for: $q")

        return when {
            // Greetings
            q.contains(Regex("\\b(hello|hi|hey|good morning|good afternoon|good evening)\\b")) -> {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> "Good morning! I'm your AI assistant. How can I help you today?"
                    hour < 17 -> "Good afternoon! I'm here to answer your questions and assist you."
                    hour < 21 -> "Good evening! What can I help you with?"
                    else -> "Hello! I'm your intelligent voice assistant. Ask me anything!"
                }
            }

            // Weather questions
            q.contains(Regex("\\b(weather|temperature|hot|cold|rain|sunny|cloudy)\\b")) ->
                "I don't have access to real-time weather data, but I recommend checking your weather app or asking 'Hey Google, what's the weather like?' for current conditions in your area."

            // Math and calculations - improved pattern matching
            q.contains(Regex("\\b(calculate|compute|math|plus|add|minus|subtract|multiply|divide|times|\\+|\\-|\\*|/)\\b")) ||
                    q.matches(Regex(".*\\d+\\s*[+\\-*/]\\s*\\d+.*")) -> {
                val mathResult = calculateMath(q)
                if (mathResult.contains("equals")) mathResult
                else "I can help with math! Try asking something like 'What's 15 plus 27?' or 'Calculate 8 times 9'."
            }

            // Time
            q.contains(Regex("\\b(what time|current time|time now)\\b")) -> {
                val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                "The current time is $time."
            }

            // Date
            q.contains(Regex("\\b(what date|today|current date)\\b")) -> {
                val date = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                "Today is $date."
            }

            // Science questions
            q.contains(Regex("\\b(how does|why does|what is|explain|science|physics|chemistry|biology)\\b")) ->
                "That's a great question about ${extractMainTopic(q)}! For detailed scientific information, I recommend searching online or consulting specialized resources."

            // Technology questions
            q.contains(Regex("\\b(computer|phone|internet|wifi|app|software|android|technology)\\b")) ->
                "Technology questions are fascinating! For detailed tech support or information about ${extractMainTopic(q)}, I'd recommend checking official documentation or tech support resources."

            // Health questions
            q.contains(Regex("\\b(health|medicine|doctor|symptom|pain|sick|headache)\\b")) ->
                "For health-related questions, I always recommend consulting with healthcare professionals or trusted medical resources. Your health is important!"

            // Jokes and entertainment
            q.contains(Regex("\\b(joke|funny|laugh|humor|entertainment)\\b")) -> {
                val jokes = listOf(
                    "Why don't scientists trust atoms? Because they make up everything!",
                    "Why did the scarecrow win an award? Because he was outstanding in his field!",
                    "What do you call a fake noodle? An impasta!",
                    "Why don't eggs tell jokes? They'd crack each other up!",
                    "What do you call a bear with no teeth? A gummy bear!",
                    "Why did the bicycle fall over? Because it was two tired!",
                    "What do you call a dinosaur that crashes his car? Tyrannosaurus Wrecks!",
                    "Why don't skeletons fight each other? They don't have the guts!"
                )
                jokes.random()
            }

            // Motivational and quotes
            q.contains(Regex("\\b(motivate|inspire|quote|wisdom|advice)\\b")) -> {
                val quotes = listOf(
                    "The only way to do great work is to love what you do. - Steve Jobs",
                    "Innovation distinguishes between a leader and a follower. - Steve Jobs",
                    "The future belongs to those who believe in the beauty of their dreams. - Eleanor Roosevelt",
                    "Success is not final, failure is not fatal: it is the courage to continue that counts. - Winston Churchill",
                    "Be yourself; everyone else is already taken. - Oscar Wilde"
                )
                quotes.random()
            }

            // Personal questions about the assistant
            q.contains(Regex("\\b(who are you|what are you|your name|about you)\\b")) ->
                "I'm your intelligent voice assistant! I can answer questions, help with calculations, tell jokes, give you the time and date, open apps, set alarms, and much more. What would you like to know?"

            // Capabilities
            q.contains(Regex("\\b(what can you do|help me|capabilities|features)\\b")) ->
                "I can help you with many things! I can answer questions, do math calculations, tell jokes and quotes, give you the time and date, open apps on your phone, set alarms, search the web, get directions, and even scan text from images using the camera. What would you like to try?"

            // Thanks and appreciation
            q.contains(Regex("\\b(thank|thanks|appreciate|good job|well done)\\b")) ->
                "You're very welcome! I'm always here to help. Is there anything else you'd like to know or do?"

            // Goodbye
            q.contains(Regex("\\b(bye|goodbye|see you|farewell|exit|quit)\\b")) ->
                "Goodbye! It was great helping you today. Feel free to ask me anything anytime!"

            // Default intelligent response
            else -> {
                val responses = listOf(
                    "That's an interesting question about '${extractMainTopic(q)}'. While I can help with many things like calculations, jokes, time/date, opening apps, and basic questions, for detailed information I'd recommend searching online.",
                    "I understand you're asking about '${extractMainTopic(q)}'. I can assist with math, time queries, app controls, jokes, and basic Q&A. For more complex topics, try a web search or specialized resources.",
                    "Great question! While I can help with calculations, device controls, entertainment, and simple queries, for comprehensive information about '${extractMainTopic(q)}', I recommend checking reliable online sources."
                )
                responses.random()
            }
        }
    }

    private fun extractMainTopic(question: String): String {
        val words = question.split(" ")
        val importantWords = words.filter { word ->
            word.length > 3 && !listOf(
                "what", "where", "when", "how", "why", "does", "will", "can",
                "the", "and", "but", "for", "with", "this", "that", "they",
                "them", "there", "their"
            ).contains(word.lowercase())
        }
        return importantWords.take(2).joinToString(" ").ifEmpty { "that topic" }
    }

    private fun calculateMath(question: String): String {
        return try {
            // More flexible patterns that match "5 + 2" format
            val addPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:plus|\\+|add|added to)\\s*(\\d+(?:\\.\\d+)?)")
            val subtractPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:minus|\\-|subtract|take away)\\s*(\\d+(?:\\.\\d+)?)")
            val multiplyPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:times|\\*|multiply|multiplied by|x)\\s*(\\d+(?:\\.\\d+)?)")
            val dividePattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:divided by|\\/|divide)\\s*(\\d+(?:\\.\\d+)?)")

            when {
                addPattern.find(question) != null -> {
                    val match = addPattern.find(question)!!
                    val num1 = match.groupValues[1].toDouble()
                    val num2 = match.groupValues[2].toDouble()
                    val result = num1 + num2
                    "$num1 plus $num2 equals ${formatNumber(result)}"
                }
                subtractPattern.find(question) != null -> {
                    val match = subtractPattern.find(question)!!
                    val num1 = match.groupValues[1].toDouble()
                    val num2 = match.groupValues[2].toDouble()
                    val result = num1 - num2
                    "$num1 minus $num2 equals ${formatNumber(result)}"
                }
                multiplyPattern.find(question) != null -> {
                    val match = multiplyPattern.find(question)!!
                    val num1 = match.groupValues[1].toDouble()
                    val num2 = match.groupValues[2].toDouble()
                    val result = num1 * num2
                    "$num1 times $num2 equals ${formatNumber(result)}"
                }
                dividePattern.find(question) != null -> {
                    val match = dividePattern.find(question)!!
                    val num1 = match.groupValues[1].toDouble()
                    val num2 = match.groupValues[2].toDouble()
                    if (num2 != 0.0) {
                        val result = num1 / num2
                        "$num1 divided by $num2 equals ${formatNumber(result)}"
                    } else {
                        "I can't divide by zero! That's mathematically undefined."
                    }
                }
                else -> "I can help with math! Try asking 'What's 15 plus 27?' or 'Calculate 8 times 9'."
            }
        } catch (e: Exception) {
            "I couldn't parse that math problem. Try a format like '10 plus 5' or '20 times 3'."
        }
    }

    private fun formatNumber(number: Double): String {
        return if (number == number.toInt().toDouble()) {
            number.toInt().toString()
        } else {
            String.format("%.2f", number)
        }
    }

    suspend fun processImageText(extractedText: String): String {
        val prompt = "I scanned this text from an image: '$extractedText'. Please read it back to me and tell me what it might be about."
        return getIntelligentResponse(prompt)
    }
}
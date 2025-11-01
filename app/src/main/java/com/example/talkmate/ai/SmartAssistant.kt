package com.example.talkmate.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import kotlin.random.Random

sealed class AssistantResponse {
    data class Text(val message: String) : AssistantResponse()
    data class Action(val message: String, val intent: Intent) : AssistantResponse()
}

class SmartAssistant(private val context: Context) {

    private val geminiAIService = GeminiAIService()

    suspend fun processCommand(input: String): AssistantResponse {
        val command = input.lowercase(Locale.getDefault()).trim()

        return when {
            // System control commands - handle locally for instant response
            command.contains(Regex("\\b(set alarm|alarm|timer|wake me|remind me)\\b")) ->
                setAlarm(command)

            // Open apps - handle locally
            command.contains(Regex("\\b(open|launch|start)\\b")) &&
                    command.contains(Regex("\\b(camera|photos|gallery|music|contacts|calendar|settings)\\b")) ->
                openApp(command)

            // Navigation - handle locally
            command.contains(Regex("\\b(navigate|directions|route|how to get|take me to)\\b")) ->
                getDirections(command)

            // Search queries - handle locally for system integration
            command.contains(Regex("\\b(search|google|find|look up)\\b")) ->
                performSearch(command)

            // Simple time/date - can be handled locally for speed
            command.contains(Regex("\\bwhat time\\b")) && !command.contains(Regex("\\b(in|at)\\b")) ->
                AssistantResponse.Text(getCurrentTime())

            command.contains(Regex("\\btoday\\b|\\bwhat day\\b")) && !command.contains(Regex("\\b(is|in)\\b")) ->
                AssistantResponse.Text(getCurrentDate())

            // Media control
            command.contains(Regex("\\b(play|music|song|playlist|video)\\b")) ->
                playMedia(command)

            // Everything else - send to AI for intelligent response
            else -> {
                val aiResponse = withContext(Dispatchers.IO) {
                    geminiAIService.getIntelligentResponse(input)
                }
                AssistantResponse.Text(aiResponse)
            }
        }
    }

    suspend fun processImageText(extractedText: String): String {
        return try {
            withContext(Dispatchers.IO) {
                geminiAIService.processImageText(extractedText)
            }
        } catch (e: Exception) {
            "I scanned this text: '$extractedText'. While I can't analyze it with AI right now, I can read it aloud for you."
        }
    }

    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "The current time is ${formatter.format(Date())}."
    }

    private fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return "Today is ${formatter.format(Date())}."
    }

    private fun setAlarm(command: String): AssistantResponse {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Extract time if mentioned
            val timePattern = Regex(
                "(\\d{1,2})(?::(\\d{2}))?"
                        + "(?:\\s*(am|pm|AM|PM))?"
                        + "|(?:in\\s+(\\d+)\\s*(minute|minutes|hour|hours))"
            )
            val match = timePattern.find(command)

            if (match != null) {
                val hour = match.groupValues[1].toIntOrNull()
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val amPm = match.groupValues[3].lowercase()
                val duration = match.groupValues[4].toIntOrNull()
                val unit = match.groupValues[5]

                when {
                    hour != null -> {
                        val finalHour = when {
                            amPm == "pm" && hour != 12 -> hour + 12
                            amPm == "am" && hour == 12 -> 0
                            else -> hour
                        }
                        intent.putExtra(AlarmClock.EXTRA_HOUR, finalHour)
                        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Assistant Alarm")
                    }

                    duration != null -> {
                        val minutes = when (unit) {
                            "hour", "hours" -> duration * 60
                            else -> duration
                        }
                        intent.putExtra(
                            AlarmClock.EXTRA_LENGTH,
                            minutes * 60 * 1000
                        ) // milliseconds
                        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Assistant Timer")
                    }
                }
            }

            AssistantResponse.Action("Opening alarm app to set your alarm...", intent)
        } catch (e: Exception) {
            AssistantResponse.Text("I can help you set alarms! Try saying 'set alarm for 7:30 AM' or 'set timer for 10 minutes'.")
        }
    }

    private fun openApp(command: String): AssistantResponse {
        val intent = when {
            command.contains("camera") -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            command.contains("gallery") || command.contains("photos") -> Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )

            command.contains("contacts") -> Intent(
                Intent.ACTION_VIEW,
                ContactsContract.Contacts.CONTENT_URI
            )

            command.contains("calendar") -> Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
            command.contains("settings") -> Intent(android.provider.Settings.ACTION_SETTINGS)
            else -> null
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AssistantResponse.Action("Opening app for you...", intent)
        } else {
            AssistantResponse.Text("I can help you open camera, gallery, contacts, calendar, or settings. Which app would you like to open?")
        }
    }

    private fun performSearch(command: String): AssistantResponse {
        val searchQuery = command.replace(
            Regex("\\b(search|google|find|look up|what is|who is|tell me about)\\b"),
            ""
        ).trim()
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", searchQuery)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return AssistantResponse.Action("Searching the web for: $searchQuery", intent)
    }

    private fun getDirections(command: String): AssistantResponse {
        val destination =
            command.replace(Regex("\\b(navigate|directions|route|how to get|take me to)\\b"), "")
                .trim()
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$destination")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return AssistantResponse.Action("Getting directions to: $destination", intent)
    }

    private fun playMedia(command: String): AssistantResponse {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return AssistantResponse.Action("Opening music player...", intent)
    }
}
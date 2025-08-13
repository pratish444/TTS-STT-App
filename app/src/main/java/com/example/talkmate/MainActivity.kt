package com.example.ttssttapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.talkmate.ui.theme.TTSSTTAppTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var messages = mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var isListening by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var isPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isPermissionGranted = isGranted
        if (!isGranted) {
            errorMessage = "Microphone permission is required to use this feature."
        } else {
            setupSpeechRecognizer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Request permission
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            TTSSTTAppTheme {
                VoiceAssistantScreen()
            }
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer?.destroy() // Clean up any existing instance
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d("MainActivity", "SpeechRecognizer initialized successfully")
            } else {
                errorMessage = "Speech recognition is not available on this device."
                Log.e("MainActivity", errorMessage)
            }
        } catch (e: Exception) {
            errorMessage = "Failed to initialize Speech Recognizer. Try using a physical device instead of emulator."
            Log.e("MainActivity", "Error creating SpeechRecognizer", e)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("MainActivity", "onReadyForSpeech")
                isListening = true
                errorMessage = ""
            }

            override fun onBeginningOfSpeech() {
                Log.d("MainActivity", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level feedback - you could use this for visual feedback
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("MainActivity", "onEndOfSpeech")
                isListening = false
            }

            override fun onError(error: Int) {
                Log.e("MainActivity", "Speech recognition error: $error")
                isListening = false
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Try using a physical device."
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error. Try restarting the app."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error. Check your internet connection."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Check your connection."
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please speak clearly and try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is busy. Try again in a moment."
                    SpeechRecognizer.ERROR_SERVER -> "Server error. Try again later."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try speaking again."
                    else -> "Speech recognition error ($error). Try again."
                }
            }

            override fun onResults(results: Bundle?) {
                Log.d("MainActivity", "onResults")
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userText = matches[0]
                    Log.d("MainActivity", "Recognized: $userText")

                    val newMessages = messages.value.toMutableList()
                    newMessages.add("user" to userText)
                    val assistantResponse = generateResponse(userText)
                    newMessages.add("assistant" to assistantResponse)
                    messages.value = newMessages
                    speakText(assistantResponse)
                } else {
                    errorMessage = "No speech was recognized. Please try again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d("MainActivity", "Partial result: ${matches[0]}")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VoiceAssistantScreen() {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(messages.value.size) {
            if (messages.value.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.value.size - 1)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Voice Assistant", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 16.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages.value) { (sender, text) ->
                        MessageBubble(sender = sender, text = text)
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (!isPermissionGranted) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@FloatingActionButton
                            }

                            if (isListening) {
                                stopListening()
                            } else {
                                startListening()
                            }
                        },
                        containerColor = when {
                            !isPermissionGranted -> MaterialTheme.colorScheme.outline
                            isListening -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Microphone")
                    }

                    Text(
                        text = when {
                            !isPermissionGranted -> "Tap to grant permission"
                            isListening -> "Listening... Speak now!"
                            else -> "Tap the mic to talk"
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    @Composable
    fun MessageBubble(sender: String, text: String) {
        val isUser = sender == "user"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (!isUser) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { speakText(text) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.VolumeUp, "Speak", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            errorMessage = "Speech recognizer not initialized. Try restarting the app."
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

            // Add these for better recognition
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d("MainActivity", "Started listening")
        } catch (e: SecurityException) {
            errorMessage = "Microphone permission not granted."
            Log.e("MainActivity", "SecurityException when starting listening", e)
        } catch (e: Exception) {
            errorMessage = "Failed to start listening: ${e.message}"
            Log.e("MainActivity", "Exception when starting listening", e)
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        Log.d("MainActivity", "Stopped listening")
    }

    private fun generateResponse(userInput: String): String {
        val input = userInput.lowercase(Locale.getDefault()).trim()
        return when {
            input.contains("hello") || input.contains("hi") || input.contains("hey") ->
                "Hello there! How can I help you today?"
            input.contains("time") ->
                "The current time is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            input.contains("date") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())}."
            input.contains("joke") ->
                "Why did the scarecrow win an award? Because he was outstanding in his field!"
            input.contains("weather") ->
                "I can't check the weather right now, but I hope it's nice where you are!"
            input.contains("thank") ->
                "You're very welcome! Is there anything else I can help you with?"
            input.contains("bye") || input.contains("goodbye") ->
                "Goodbye! Have a great day!"
            else ->
                "I heard you say: '$userInput'. How can I help you with that?"
        }
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("MainActivity", "Language not supported for TTS")
                errorMessage = "Text-to-Speech language not supported."
            } else {
                Log.d("MainActivity", "TTS initialized successfully")
            }
        } else {
            errorMessage = "Could not initialize Text-to-Speech."
            Log.e("MainActivity", "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
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
    // Note: We are no longer using `lateinit` to prevent crashes if initialization fails.
    private var speechRecognizer: SpeechRecognizer? = null

    private var messages = mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var isListening by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            errorMessage = "Microphone permission is required to use this feature."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permission immediately
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Initialize Speech Recognizer safely
        setupSpeechRecognizer()

        setContent {
            TTSSTTAppTheme {
                VoiceAssistantScreen()
            }
        }
    }

    // --- THIS IS THE CRITICAL FIX ---
    // This function now safely handles cases where the recognizer can't be created.
    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
            } else {
                errorMessage = "Speech recognition is not available on this device."
                Log.e("MainActivity", errorMessage)
            }
        } catch (e: Exception) {
            // This will catch errors on emulators or devices without Google services
            errorMessage = "Failed to initialize Speech Recognizer. Your device/emulator may not support it. Please ensure the Google App is installed and enabled."
            Log.e("MainActivity", "Error creating SpeechRecognizer", e)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                errorMessage = ""
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error."
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "An unknown recognition error occurred."
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { userText ->
                    val newMessages = messages.value.toMutableList()
                    newMessages.add("user" to userText)
                    val assistantResponse = generateResponse(userText)
                    newMessages.add("assistant" to assistantResponse)
                    messages.value = newMessages
                    speakText(assistantResponse)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
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
                    title = { Text(stringResource(id = R.string.app_name), fontWeight = FontWeight.Bold) },
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
                    Text(
                        text = errorMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (isListening) stopListening() else startListening()
                        },
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Microphone")
                    }
                    Text(
                        text = if (isListening) "Listening..." else "Tap the mic to talk",
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
        // Safe-guard: Don't start if the recognizer is null
        if (speechRecognizer == null) {
            errorMessage = "Cannot start listening, speech recognizer is not available."
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening now...")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            errorMessage = "Microphone permission not granted."
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun generateResponse(userInput: String): String {
        val input = userInput.lowercase(Locale.getDefault()).trim()
        return when {
            input.contains("hello") || input.contains("hi") -> "Hello there! How can I help you?"
            input.contains("time") -> "The current time is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            input.contains("joke") -> "Why did the scarecrow win an award? Because he was outstanding in his field!"
            else -> "Sorry, I didn't understand that. Could you please rephrase?"
        }
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        } else {
            errorMessage = "Could not initialize Text-to-Speech."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
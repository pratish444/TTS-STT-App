package com.example.talkmate.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.talkmate.ui.components.CurrentTextDisplay
import com.example.talkmate.ui.components.MessageList
import com.example.talkmate.ui.components.SpeechButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechAssistantScreen(
    transcribedText: String,
    assistantResponse: String,
    messages: List<Pair<String, String>>,
    isListening: Boolean,
    errorMessage: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSpeakText: (String) -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Assistant",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearMessages) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear conversation"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error message
                    AnimatedVisibility(
                        visible = errorMessage.isNotBlank(),
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Current text display
                    CurrentTextDisplay(
                        transcribedText = transcribedText,
                        assistantResponse = assistantResponse,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speak transcribed text button
                        AnimatedVisibility(
                            visible = transcribedText.isNotBlank(),
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            OutlinedButton(
                                onClick = { onSpeakText(transcribedText) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Speak Input")
                            }
                        }

                        // Main speech button
                        SpeechButton(
                            isListening = isListening,
                            onStartListening = onStartListening,
                            onStopListening = onStopListening
                        )

                        // Speak assistant response button
                        AnimatedVisibility(
                            visible = assistantResponse.isNotBlank(),
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            FilledTonalButton(
                                onClick = { onSpeakText(assistantResponse) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Speak Reply")
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            MessageList(
                messages = messages,
                onSpeak = onSpeakText,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
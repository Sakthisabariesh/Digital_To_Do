package com.mindecho.app.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindecho.app.nlp.ParsedTaskIntent
import com.mindecho.app.nlp.TimeIntentParser
import com.mindecho.app.ui.components.WaveformCanvas
import java.util.Locale

private val SheetDarkSurface = Color(0xFF101012)
private val ChipBackground = Color(0xFF1C1C1E)
private val ChipBorder = Color(0xFF2E2E32)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val AccentBlue = Color(0xFF64B5F6)
private val AccentGreen = Color(0xFF81C784)
private val MonospaceTag = Color(0xFFB0BEC5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOverlaySheet(
    onDismissRequest: () -> Unit,
    onTaskCaptured: (rawSentence: String, cleanedTitle: String, triggerTimestamp: Long) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var liveTranscript by remember { mutableStateOf("") }
    var parsedIntent by remember { mutableStateOf<ParsedTaskIntent?>(null) }
    var rmsdB by remember { mutableFloatStateOf(0f) }
    var isListening by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing assistant...") }

    val currentOnTaskCaptured by rememberUpdatedState(onTaskCaptured)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    DisposableEffect(Unit) {
        var speechRecognizer: SpeechRecognizer? = null
        var tts: TextToSpeech? = null

        fun startListening() {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                statusMessage = "Offline speech recognizer unavailable."
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusMessage = "Listening..."
                    }

                    override fun onBeginningOfSpeech() {
                        statusMessage = "Capturing thought..."
                    }

                    override fun onRmsChanged(rmsdBValue: Float) {
                        rmsdB = rmsdBValue
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        statusMessage = "Processing intent..."
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.w("VoiceOverlay", "SpeechRecognizer error: $error")
                        if (liveTranscript.isNotEmpty()) {
                            val intent = TimeIntentParser.parse(liveTranscript)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            currentOnTaskCaptured(intent.rawSentence, intent.cleanedTitle, intent.triggerTimestamp)
                        } else {
                            statusMessage = "Tap mic to try again."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: liveTranscript

                        if (spokenText.isNotBlank()) {
                            val intent = TimeIntentParser.parse(spokenText)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            currentOnTaskCaptured(intent.rawSentence, intent.cleanedTitle, intent.triggerTimestamp)
                        } else {
                            currentOnDismissRequest()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val currentText = partials?.firstOrNull() ?: ""
                        if (currentText.isNotBlank()) {
                            liveTranscript = currentText
                            parsedIntent = TimeIntentParser.parse(currentText)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(recognizerIntent)
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        statusMessage = "MindEcho..."
                    }

                    override fun onDone(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            startListening()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            startListening()
                        }
                    }
                })

                val prompt = "What's on your mind?"
                tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "GREETING_UTTERANCE")
            } else {
                startListening()
            }
        }

        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("VoiceOverlay", "Error tearing down SpeechRecognizer", e)
            }

            try {
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (e: Exception) {
                Log.e("VoiceOverlay", "Error tearing down TTS", e)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SheetDarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF333336))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "[MINDECHO OFFLINE HUD]",
                color = AccentBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusMessage,
                color = MonospaceTag,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(20.dp))

            WaveformCanvas(
                rmsdB = rmsdB,
                isListening = isListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChipBackground)
                    .border(1.dp, ChipBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (liveTranscript.isNotEmpty()) "\"$liveTranscript\"" else "Listening for idea or reminder...",
                    color = if (liveTranscript.isNotEmpty()) TextWhite else TextSecondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }

            AnimatedVisibility(
                visible = parsedIntent != null && liveTranscript.isNotBlank(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parsedIntent?.let { intent ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF18181A))
                                .border(1.dp, Color(0xFF2C2C30), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTION:",
                                    color = AccentBlue,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = intent.cleanedTitle,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (intent.hasScheduledTime) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF142218))
                                    .border(1.dp, Color(0xFF1B3822), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TIME:",
                                        color = AccentGreen,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = TimeIntentParser.formatScheduledTime(intent.triggerTimestamp),
                                        color = Color(0xFFA5D6A7),
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

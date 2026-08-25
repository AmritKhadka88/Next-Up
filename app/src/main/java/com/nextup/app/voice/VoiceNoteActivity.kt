package com.nextup.app.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nextup.app.R
import com.nextup.app.data.SourceType
import com.nextup.app.data.TaskDatabase
import com.nextup.app.parser.FillerPhraseRepository
import com.nextup.app.parser.LearnedWordsRepository
import com.nextup.app.parser.RuleLibraryRepository
import com.nextup.app.parser.TaskParser
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

/**
 * Continuous hands-free note-taking: listen -> parse -> save -> speak a short confirmation
 * -> listen again, looping until the user says something like "no" / "that's all", at which
 * point it speaks a short goodbye and closes itself.
 */
class VoiceNoteActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var notedCount = 0

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var micIcon: ImageView

    private val stopPhrases = listOf(
        "no", "no thanks", "nothing else", "that's all", "that is all",
        "i'm done", "im done", "stop", "that's it", "nope", "no more", "done"
    )

    private val confirmations = listOf("Noted.", "Got it.", "Done.", "Saved.", "Okay, noted.", "Added.")
    private val followUps = listOf("Anything else?", "What else?", "Next one?", "Go ahead.", "")
    private val goodbyes = listOf("Alright, done!", "Okay, closing up.", "Got it, see you later!", "All set!")

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initTts() else {
            Toast.makeText(this, "Voice mode needs microphone access", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_note)

        statusText = findViewById(R.id.textVoiceStatus)
        logText = findViewById(R.id.textVoiceLog)
        micIcon = findViewById(R.id.imageMicIndicator)
        findViewById<Button>(R.id.buttonStopVoiceMode).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initTts()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == UTTERANCE_GOODBYE) {
                            runOnUiThread { finish() }
                        } else {
                            runOnUiThread { startListening() }
                        }
                    }
                })
                ttsReady = true
                speak("Listening. Say your first note.", UTTERANCE_PROMPT)
            }
        }
    }

    private fun startListening() {
        statusText.text = getString(R.string.voice_listening)
        micIcon.alpha = 1f

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { micIcon.alpha = 0.6f }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { micIcon.alpha = 1f }

            override fun onError(error: Int) {
                // No speech heard / timeout — just listen again rather than giving up.
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    startListening()
                } else {
                    speak("Sorry, I had trouble hearing that. Try again.", UTTERANCE_PROMPT)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim()
                if (transcript.isNullOrBlank()) {
                    startListening()
                    return
                }
                handleTranscript(transcript)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun handleTranscript(transcript: String) {
        val normalized = transcript.trim().trimEnd('.', '!', '?').lowercase()

        if (normalized in stopPhrases) {
            speak(goodbyes.random(), UTTERANCE_GOODBYE)
            return
        }

        statusText.text = "Saving..."
        lifecycleScope.launch {
            val excluded = LearnedWordsRepository(this@VoiceNoteActivity).getExcludedPhrases()
            val rules = RuleLibraryRepository(this@VoiceNoteActivity).getRules()
            val fillers = FillerPhraseRepository(this@VoiceNoteActivity).getLearnedPhrases().map { it.phrase }
            val parsed = TaskParser.parse(transcript, excluded, rules, fillers)
            val task = parsed.toTask(source = SourceType.SPEECH)

            val newId = TaskDatabase.getInstance(this@VoiceNoteActivity).taskDao().insert(task)
            com.nextup.app.widget.WidgetUpdater.refreshAll(this@VoiceNoteActivity)
            if (task.hasAlarm) {
                com.nextup.app.alarm.AlarmScheduler.schedule(this@VoiceNoteActivity, task.copy(id = newId))
            }

            notedCount++
            logText.text = "$notedCount note(s) added this session\nLast: \"${parsed.title}\""

            val confirmation = confirmations.random()
            val followUp = followUps.random()
            val phrase = if (followUp.isBlank()) confirmation else "$confirmation $followUp"
            speak(phrase, UTTERANCE_PROMPT)
        }
    }

    private fun speak(text: String, utteranceId: String) {
        statusText.text = text
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        private const val UTTERANCE_PROMPT = "prompt"
        private const val UTTERANCE_GOODBYE = "goodbye"
    }
}

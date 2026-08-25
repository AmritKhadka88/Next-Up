package com.nextup.app.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.appcompat.app.AlertDialog
import java.util.Locale

object VoicePickerDialog {

    fun show(context: Context, currentVoiceName: String?, onVoiceChosen: (Voice) -> Unit) {
        var tempTts: TextToSpeech? = null
        tempTts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech

            val deviceLanguage = Locale.getDefault().language
            val voices = tempTts?.voices
                ?.filter { it.locale.language == deviceLanguage && !it.isNetworkConnectionRequired }
                ?.sortedBy { it.name }
                ?: emptyList()

            val displayList = voices.ifEmpty {
                // Fall back to any same-language voice, even ones needing network, rather
                // than showing an empty list.
                tempTts?.voices?.filter { it.locale.language == deviceLanguage }?.sortedBy { it.name } ?: emptyList()
            }

            if (displayList.isEmpty()) {
                android.widget.Toast.makeText(context, "No alternate voices found for your language", android.widget.Toast.LENGTH_SHORT).show()
                tempTts?.shutdown()
                return@TextToSpeech
            }

            val names = displayList.map { formatVoiceName(it) }.toTypedArray()
            val currentIndex = displayList.indexOfFirst { it.name == currentVoiceName }.let { if (it < 0) 0 else it }

            AlertDialog.Builder(context)
                .setTitle("Choose a voice")
                .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                    val chosen = displayList[which]
                    // Preview it immediately so the choice is obvious.
                    tempTts?.speak("This is how I'll sound.", TextToSpeech.QUEUE_FLUSH, null, "preview")
                    tempTts?.voice = chosen
                    onVoiceChosen(chosen)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setOnDismissListener { tempTts?.shutdown() }
                .show()
        }
    }

    private fun formatVoiceName(voice: Voice): String {
        // Voice.name is usually something like "en-us-x-sfg-local" — trim it to something readable.
        val readable = voice.name.replace(Regex("""[_\-.]"""), " ").trim()
        val quality = if (voice.quality >= Voice.QUALITY_HIGH) " (high quality)" else ""
        return readable + quality
    }
}

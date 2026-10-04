package com.glove.browser

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object Voice {
    fun punctuate(text: String, finished: Boolean): String {
        var value = text.replace(Regex("\\s+"), " ").trim()
        if (value.isEmpty()) return ""
        value = value.replace(Regex("\\s+([,.;:!?])"), "$1")
        if (finished && !value.last().toString().matches(Regex("[.!?…]"))) value += "."
        return value.replaceFirstChar { it.uppercaseChar() }
    }

    fun listen(activity: AppCompatActivity, onText: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            android.widget.Toast.makeText(activity, "Распознавание речи недоступно", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val title = TextView(activity).apply {
            text = "Glove"
            textSize = 20f
            setPadding(0, 0, 0, 12)
        }
        val body = TextView(activity).apply {
            text = "Слушаю…"
            textSize = 16f
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 16)
            addView(title)
            addView(body)
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(box)
            .setNegativeButton("Готово", null)
            .create()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        var latest = ""
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (latest.isNotBlank()) onText(punctuate(latest, true))
                if (dialog.isShowing) dialog.dismiss()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    latest = text
                    body.text = punctuate(text, true)
                    onText(punctuate(text, true))
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    latest = text
                    body.text = punctuate(text, false)
                    body.gravity = Gravity.START
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra("android.speech.extra.ENABLE_FORMATTING", "quality")
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
        }
        dialog.setOnDismissListener { recognizer.destroy() }
        dialog.show()
        recognizer.startListening(intent)
    }
}

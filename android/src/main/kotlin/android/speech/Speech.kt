package android.speech

import android.content.Context

object SpeechRecognizer {
    @JvmStatic
    fun isRecognitionAvailable(context: Context?): Boolean = false
}

object RecognizerIntent {
    const val ACTION_RECOGNIZE_SPEECH = "android.speech.action.RECOGNIZE_SPEECH"
    const val EXTRA_LANGUAGE_MODEL = "android.speech.extra.LANGUAGE_MODEL"
    const val LANGUAGE_MODEL_FREE_FORM = "free_form"
    const val EXTRA_PROMPT = "android.speech.extra.PROMPT"
    const val EXTRA_LANGUAGE = "android.speech.extra.LANGUAGE"
    const val EXTRA_RESULTS = "android.speech.extra.RESULTS"
}

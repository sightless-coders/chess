package org.sightlesscoders.chess;

import java.util.Locale;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Text-to-speech wrapper for spoken feedback, following the pattern used by
 * Street Fire Arena's Android port (TTSEngine.java). Non-blocking: speak()
 * returns immediately while TTS plays in the background.
 */
public class TtsEngine {

    private TextToSpeech tts;
    private boolean ready;
    private float speechRate = 1.0f;

    public TtsEngine(Context context) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(speechRate);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) { }

                        @Override
                        public void onDone(String utteranceId) { }

                        @Override
                        public void onError(String utteranceId) { }
                    });
                    ready = true;
                }
            }
        });
    }

    /**
     * Speak text. Non-blocking.
     *
     * @param text      the text to speak
     * @param interrupt if true, stop current speech first
     */
    public void speak(String text, boolean interrupt) {
        if (!ready || tts == null || text == null || text.isEmpty()) return;
        if (interrupt) tts.stop();
        tts.speak(text, interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                null, "chess_" + System.currentTimeMillis());
    }

    /** Convenience: speak, interrupting current speech. */
    public void speak(String text) {
        speak(text, true);
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void setSpeechRate(float rate) {
        this.speechRate = rate;
        if (tts != null) tts.setSpeechRate(rate);
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}

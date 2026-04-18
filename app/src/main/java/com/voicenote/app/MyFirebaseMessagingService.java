package com.voicenote.app;

import android.speech.tts.TextToSpeech;
import android.util.Log;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Locale;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "VoicePing";
    private TextToSpeech tts;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String message = data.get("message");
        String from = data.get("from");

        if (message == null && remoteMessage.getNotification() != null) {
            message = remoteMessage.getNotification().getBody();
        }
        if (message == null) return;

        Log.d(TAG, "Message received from: " + from + " — " + message);

        final String finalMessage = (from != null ? from + " — " : "") + message;

        // Text-to-Speech se bol do
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                int result = tts.setLanguage(new Locale("hi", "IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
                tts.speak(finalMessage, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "New FCM token: " + token);
        // Token Firebase RTDB mein save karo
        String userId = getSharedPreferences("VoiceNotePrefs", MODE_PRIVATE)
                .getString("user_id", null);
        if (userId != null) {
            String safeId = userId.replace(".", "_");
            FirebaseDatabase.getInstance()
                    .getReference("tokens/" + safeId)
                    .setValue(token);
            Log.d(TAG, "Token saved for user: " + userId);
        }
    }
}

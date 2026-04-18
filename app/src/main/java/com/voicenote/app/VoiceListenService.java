package com.voicenote.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.database.*;
import com.google.firebase.database.ChildEventListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * VoiceListenService - Hamesha background me chalne wali service
 *
 * YEH KYA KARTA HAI:
 * 1. Phone background me rakha ho - service sun rahi hoti hai
 * 2. Aap bolte ho "Jagtar ko meeting karni hai"
 * 3. Service detect karti hai → audio record karti hai → Firebase se Jagtar ko bhejti hai
 * 4. Jagtar ko notification aati hai aur auto voice sunai deta hai
 *
 * FOREGROUND SERVICE KYU?
 * - Android background apps ko kill karta hai battery bachane ke liye
 * - Foreground service notification dikhata hai aur always alive rehta hai
 * - Yeh official Android tarika hai background microphone use karne ka
 */
public class VoiceListenService extends Service {

    private static final String TAG = "VoiceListenService";
    private static final String CHANNEL_LISTEN = "vn_listening";
    private static final String CHANNEL_INBOX   = "vn_inbox";
    private static final int    NOTIF_LISTEN    = 1;
    private static final int    NOTIF_INBOX     = 2;

    // Speech recognition
    private SpeechRecognizer speechRec;
    private boolean isListening = false;
    private Handler restartHandler = new Handler(Looper.getMainLooper());

    // Audio recording
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecordingAudio = false;

    // Text-to-Speech
    private static TextToSpeech tts;

    // Firebase
    private DatabaseReference firebaseDb;

    // User info & contacts
    private String myName, myId;
    private List<MainActivity.Contact> contacts = new ArrayList<>();

    // ── LIFECYCLE ─────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        // Load user info
        SharedPreferences prefs = getSharedPreferences("VoiceNotePrefs", MODE_PRIVATE);
        myName = prefs.getString("name", "");
        myId   = prefs.getString("id", "");

        // Load contacts
        loadContacts(prefs);

        // Init Firebase
        firebaseDb = FirebaseDatabase.getInstance().getReference();

        // Init Text-to-Speech
        initTTS();

        // Create notification channels
        createNotificationChannels();

        // Firebase inbox sun'na shuru karo
        if (myId != null && !myId.isEmpty()) {
            listenForInbox();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Handle UPDATE_CONTACTS action from MainActivity
        if (intent != null && "UPDATE_CONTACTS".equals(intent.getAction())) {
            SharedPreferences prefs = getSharedPreferences("VoiceNotePrefs", MODE_PRIVATE);
            loadContacts(prefs);
            return START_STICKY;
        }

        // Start foreground with notification
        startForeground(NOTIF_LISTEN, buildListenNotification("Sun raha hoon..."));

        // Start speech recognition
        if (!isListening) {
            startSpeechRec();
        }

        // START_STICKY = restart if killed by system
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
        if (speechRec != null) speechRec.destroy();
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); mediaRecorder.release(); } catch(Exception e){}
        }
        restartHandler.removeCallbacksAndMessages(null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── SPEECH RECOGNITION ────────────────────────────────────────────────

    /** Speech recognition shuru karo */
    private void startSpeechRec() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available");
            return;
        }

        isListening = true;

        // UI thread pe speech recognizer banana padta hai
        new Handler(Looper.getMainLooper()).post(() -> {
            if (speechRec != null) {
                speechRec.destroy();
            }

            speechRec = SpeechRecognizer.createSpeechRecognizer(this);
            speechRec.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle p) {
                    Log.d(TAG, "Ready to listen");
                    updateNotification("Sun raha hoon...");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started");
                    // Audio recording shuru karo jab user bolna shuru kare
                    startAudioRecording();
                }

                @Override
                public void onResults(Bundle results) {
                    // Final recognized text
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);

                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        Log.d(TAG, "Recognized: " + text);

                        // Check for voice command like "Jagtar ko meeting karni hai"
                        CommandResult cmd = parseCommand(text);
                        if (cmd != null) {
                            // COMMAND DETECTED! Audio recording rok ke bhejo
                            handleVoiceCommand(cmd, text);
                        } else {
                            // Normal speech - not a send command, reset
                            stopAudioRecording(false);
                            restartSpeechRec();
                        }
                    } else {
                        stopAudioRecording(false);
                        restartSpeechRec();
                    }
                }

                @Override
                public void onError(int error) {
                    // no-speech aur aborted normal hain - restart karo
                    if (error != SpeechRecognizer.ERROR_NO_MATCH
                            && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Log.e(TAG, "Speech error: " + error);
                    }
                    stopAudioRecording(false);
                    restartSpeechRec();
                }

                @Override public void onEndOfSpeech() { }
                @Override public void onRmsChanged(float rms) { }
                @Override public void onBufferReceived(byte[] buf) { }
                @Override public void onPartialResults(Bundle partial) { }
                @Override public void onEvent(int type, Bundle params) { }
            });

            // Listen karna shuru karo
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                    new String[]{"en-IN"});
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            // Zyada der tak sune (silence timeout badha)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);

            try {
                speechRec.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "startListening error: " + e.getMessage());
                restartSpeechRec();
            }
        });
    }

    /** Thodi der baad speech recognition phir shuru karo */
    private void restartSpeechRec() {
        restartHandler.removeCallbacksAndMessages(null);
        restartHandler.postDelayed(() -> {
            if (isListening) {
                startSpeechRec();
            }
        }, 800); // 800ms baad restart
    }

    // ── COMMAND DETECTION ─────────────────────────────────────────────────

    /** "[Name] ko [message]" ya "[Name] ko bulao" pattern dhundho */
    private CommandResult parseCommand(String text) {
        if (text == null || text.length() < 4) return null;
        String lower = text.toLowerCase().trim()
                .replace("को", " ko ").replaceAll("\\s+", " ");

        // "ko bulao" — urgent ping
        if (lower.matches(".*\\bko bulao$")) {
            String beforeKo = lower.replaceAll("\\s*ko bulao$", "").trim();
            String[] words = beforeKo.split("\\s+");
            for (String w : words) {
                for (MainActivity.Contact c : contacts) {
                    if (c.name.toLowerCase().equals(w) || c.id.split("_")[0].equals(w)) {
                        return new CommandResult(c, "Aapko bula rahe hain! 🔔", text, true);
                    }
                }
            }
        }

        int koIdx = lower.indexOf(" ko ");
        if (koIdx < 1) return null;

        String beforeKo = lower.substring(0, koIdx).trim();
        String message   = text.substring(koIdx + 4).trim();
        if (message.length() < 2) return null;

        String[] words = beforeKo.split("\\s+");
        for (String w : words) {
            if (w.length() < 2) continue;
            for (MainActivity.Contact c : contacts) {
                if (c.name.toLowerCase().equals(w) || c.id.split("_")[0].equals(w)) {
                    return new CommandResult(c, message, text, false);
                }
            }
        }

        Log.d(TAG, "Contact not found in: " + text);
        return null;
    }

    /** Firebase inbox listen karo — message aaye to speak karo */
    private void listenForInbox() {
        String safeId = myId.replace(".", "_");
        firebaseDb.child("inbox").child(safeId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snap, String prev) {
                        try {
                            String from    = snap.child("from").getValue(String.class);
                            String message = snap.child("message").getValue(String.class);
                            Boolean bulao  = snap.child("bulao").getValue(Boolean.class);
                            String audio   = snap.child("audio").getValue(String.class);

                            if (message == null) return;

                            if (Boolean.TRUE.equals(bulao)) {
                                // Urgent ping — zyada awaaz se bolo
                                showInboxNotification(VoiceListenService.this,
                                        from != null ? from : "Kisi ne",
                                        "Aapko bula rahe hain! 🔔");
                                speakMessage(VoiceListenService.this,
                                        (from != null ? from : "Kisi ne") + " bula rahe hain");
                            } else {
                                showInboxNotification(VoiceListenService.this,
                                        from != null ? from : "Kisi ne", message);
                                if (audio != null && !audio.isEmpty()) {
                                    playAudio(VoiceListenService.this, audio);
                                } else {
                                    speakMessage(VoiceListenService.this,
                                            (from != null ? from : "Kisi ne") + " ka message: " + message);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Inbox listen error: " + e.getMessage());
                        }
                    }
                    @Override public void onChildChanged(DataSnapshot s, String p) {}
                    @Override public void onChildRemoved(DataSnapshot s) {}
                    @Override public void onChildMoved(DataSnapshot s, String p) {}
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    // ── SEND VOICE NOTE ───────────────────────────────────────────────────

    /** Command detect ho gaya - ab voice note bhejo */
    private void handleVoiceCommand(CommandResult cmd, String fullText) {
        Log.d(TAG, "Sending to: " + cmd.contact.name + " → " + cmd.message);
        updateNotification("Bhej raha hai → " + cmd.contact.name + "...");

        // Audio recording rok ke file bandh karo
        stopAudioRecording(true);

        // Thodi der baad bhejo (audio file properly band ho jaye)
        restartHandler.postDelayed(() -> {
            sendToFirebase(cmd, fullText);
        }, 600);
    }

    private void sendToFirebase(CommandResult cmd, String fullText) {
        if (myId == null || myId.isEmpty()) {
            Log.e(TAG, "myId not set");
            restartSpeechRec();
            return;
        }

        String recipientId = cmd.contact.id.replace(".", "_");

        // Message data
        Map<String, Object> msg = new HashMap<>();
        msg.put("from",      myName);
        msg.put("fromId",    myId);
        msg.put("message",   cmd.message);
        msg.put("fullText",  fullText);
        msg.put("timestamp", System.currentTimeMillis());
        msg.put("read",      false);
        msg.put("bulao",     cmd.bulao);

        // Audio file encode karo (agar hai)
        String audioB64 = encodeAudioFile();
        if (audioB64 != null) {
            msg.put("audio", audioB64);
        }

        // Firebase pe bhejo
        firebaseDb.child("inbox").child(recipientId).push()
                .setValue(msg)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Sent successfully to " + cmd.contact.name);
                    updateNotification("Bhej diya → " + cmd.contact.name + " ✓");

                    // Apne sent log me save karo
                    saveSentLog(cmd.contact.name, cmd.message);

                    // Confirm karne ke liye bol do (TTS)
                    speakMessage(this, cmd.contact.name + " ko bhej diya");

                    // 3 sec baad phir listening mode
                    restartHandler.postDelayed(() -> {
                        updateNotification("Sun raha hoon...");
                        startSpeechRec();
                    }, 3000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Send failed: " + e.getMessage());
                    updateNotification("Error! Internet check karo.");
                    restartHandler.postDelayed(() -> {
                        updateNotification("Sun raha hoon...");
                        startSpeechRec();
                    }, 3000);
                });
    }

    // ── AUDIO RECORDING ──────────────────────────────────────────────────

    /** Audio recording shuru karo (jab user bolna shuru kare) */
    private void startAudioRecording() {
        if (isRecordingAudio) return;

        try {
            // Temp file banao audio ke liye
            File audioFile = new File(getCacheDir(), "voice_note_" + System.currentTimeMillis() + ".3gp");
            audioFilePath = audioFile.getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecordingAudio = true;
            Log.d(TAG, "Audio recording started: " + audioFilePath);
        } catch (Exception e) {
            Log.e(TAG, "Audio recording error: " + e.getMessage());
            isRecordingAudio = false;
        }
    }

    /** Audio recording rok do */
    private void stopAudioRecording(boolean keepFile) {
        if (!isRecordingAudio || mediaRecorder == null) return;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
        } catch (Exception e) {
            Log.e(TAG, "Stop recording error: " + e.getMessage());
        }

        mediaRecorder = null;
        isRecordingAudio = false;

        if (!keepFile && audioFilePath != null) {
            new File(audioFilePath).delete();
            audioFilePath = null;
        }
    }

    /** Audio file ko Base64 me convert karo (Firebase pe bhejne ke liye) */
    private String encodeAudioFile() {
        if (audioFilePath == null) return null;

        File file = new File(audioFilePath);
        if (!file.exists() || file.length() == 0) return null;

        // 5MB se bada hai to skip karo (Firebase limit)
        if (file.length() > 5 * 1024 * 1024) {
            Log.w(TAG, "Audio too large, skipping");
            file.delete();
            return null;
        }

        try {
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytes);
            fis.close();
            file.delete(); // File delete karo storage bachane ke liye
            audioFilePath = null;
            return "data:audio/3gpp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encode error: " + e.getMessage());
            return null;
        }
    }

    // ── TEXT TO SPEECH ────────────────────────────────────────────────────

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Hindi set karo
                tts.setLanguage(new Locale("hi", "IN"));
                Log.d(TAG, "TTS ready");
            }
        });
    }

    /** Kuch bolo (Text-to-Speech) */
    public static void speakMessage(Context ctx, String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vn_" + System.currentTimeMillis());
        }
    }

    /** Base64 audio play karo */
    public static void playAudio(Context ctx, String base64Audio) {
        try {
            // Base64 decode karo
            String data = base64Audio.contains(",")
                    ? base64Audio.split(",")[1] : base64Audio;
            byte[] bytes = Base64.decode(data, Base64.NO_WRAP);

            // Temp file me save karo
            File tmpFile = new File(ctx.getCacheDir(), "play_" + System.currentTimeMillis() + ".3gp");
            FileOutputStream fos = new FileOutputStream(tmpFile);
            fos.write(bytes);
            fos.close();

            // MediaPlayer se play karo
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(tmpFile.getAbsolutePath());
            player.prepare();
            player.start();
            player.setOnCompletionListener(mp -> {
                mp.release();
                tmpFile.delete();
            });
        } catch (Exception e) {
            Log.e(TAG, "Play audio error: " + e.getMessage());
        }
    }

    // ── NOTIFICATIONS ─────────────────────────────────────────────────────

    /** New voice note ki notification dikhao */
    public static void showInboxNotification(Context ctx, String from, String message) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);

        Intent tapIntent = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_INBOX)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("📩 " + from + " ne voice note bheja!")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 300, 200, 300});

        if (nm != null) nm.notify(NOTIF_INBOX + new Random().nextInt(100), builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Always listening channel (silent)
            NotificationChannel listenCh = new NotificationChannel(
                    CHANNEL_LISTEN, "Voice Listening", NotificationManager.IMPORTANCE_LOW);
            listenCh.setDescription("Background listening status");
            listenCh.setSound(null, null);

            // Inbox notification channel
            NotificationChannel inboxCh = new NotificationChannel(
                    CHANNEL_INBOX, "Voice Note Received", NotificationManager.IMPORTANCE_HIGH);
            inboxCh.setDescription("New voice notes from contacts");

            nm.createNotificationChannel(listenCh);
            nm.createNotificationChannel(inboxCh);
        }
    }

    private Notification buildListenNotification(String status) {
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_LISTEN)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Voice Note")
                .setContentText(status)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_LISTEN, buildListenNotification(status));
    }

    // ── CONTACTS & SENT LOG ────────────────────────────────────────────────

    private void loadContacts(SharedPreferences prefs) {
        contacts = new ArrayList<>();
        String json = prefs.getString("contacts", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                contacts.add(new MainActivity.Contact(
                        o.getString("name"), o.getString("id")));
            }
        } catch (Exception e) { /* fresh */ }
        Log.d(TAG, "Loaded " + contacts.size() + " contacts");
    }

    private void saveSentLog(String to, String message) {
        SharedPreferences prefs = getSharedPreferences("VoiceNotePrefs", MODE_PRIVATE);
        String json = prefs.getString("sent", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject o = new JSONObject();
            o.put("to", to);
            o.put("message", message);
            o.put("timestamp", System.currentTimeMillis());

            // Naya message pehle add karo
            JSONArray newArr = new JSONArray();
            newArr.put(o);
            for (int i = 0; i < Math.min(arr.length(), 99); i++) newArr.put(arr.get(i));

            prefs.edit().putString("sent", newArr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    // ── HELPER CLASS ──────────────────────────────────────────────────────

    private static class CommandResult {
        MainActivity.Contact contact;
        String message;
        String fullText;
        boolean bulao;

        CommandResult(MainActivity.Contact c, String msg, String full, boolean bulao) {
            this.contact = c; this.message = msg; this.fullText = full; this.bulao = bulao;
        }
    }
}

package com.voicenote.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MainActivity - Main screen of Voice Note App
 *
 * SCREENS:
 * 1. Setup screen - enter your name on first launch
 * 2. Main screen  - inbox, contacts, sent tabs
 *
 * HOW IT WORKS:
 * - VoiceListenService runs in background always listening
 * - When you say "Jagtar ko meeting karni hai":
 *   → Service detects command
 *   → Records audio
 *   → Sends to Jagtar via Firebase
 *   → Jagtar gets notification
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERM_CODE = 101;
    private static final String PREFS = "VoiceNotePrefs";

    // User info
    private String myName, myId;
    private List<Contact> contacts = new ArrayList<>();
    private List<Message> inbox = new ArrayList<>();
    private List<SentMessage> sentLog = new ArrayList<>();

    // Firebase
    private DatabaseReference firebaseDb;

    // UI
    private LinearLayout setupScreen, mainScreen;
    private EditText setupNameInput;
    private TextView dispName, dispId, inboxBadge;
    private LinearLayout inboxList, contactsList, sentList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init Firebase
        firebaseDb = FirebaseDatabase.getInstance().getReference();

        // Find views
        setupScreen    = findViewById(R.id.setupScreen);
        mainScreen     = findViewById(R.id.mainScreen);
        setupNameInput = findViewById(R.id.setupNameInput);
        dispName       = findViewById(R.id.dispName);
        dispId         = findViewById(R.id.dispId);
        inboxBadge     = findViewById(R.id.inboxBadge);
        inboxList      = findViewById(R.id.inboxList);
        contactsList   = findViewById(R.id.contactsList);
        sentList       = findViewById(R.id.sentList);

        // Load saved user
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        myName = prefs.getString("name", "");
        myId   = prefs.getString("id", "");

        if (!myName.isEmpty()) {
            showMainScreen();
        } else {
            setupScreen.setVisibility(View.VISIBLE);
            mainScreen.setVisibility(View.GONE);
        }

        // Request permissions
        requestAllPermissions();
    }

    // ── SETUP ──────────────────────────────────────────────────────────────

    /** Called when user taps "Shuru Karo" button */
    public void onSetupDone(View v) {
        String name = setupNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Naam likho pehle", Toast.LENGTH_SHORT).show();
            return;
        }

        myName = name;
        // Generate unique ID: name + random 4-digit number
        myId = name.toLowerCase().replaceAll("\\s+", "")
                + "_" + (1000 + new Random().nextInt(9000));

        // Save to preferences
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("name", myName)
                .putString("id", myId)
                .apply();

        showMainScreen();
    }

    private void showMainScreen() {
        setupScreen.setVisibility(View.GONE);
        mainScreen.setVisibility(View.VISIBLE);

        dispName.setText("Namaste, " + myName);
        dispId.setText("ID: " + myId);

        // Load saved contacts
        loadContacts();
        // Listen for incoming messages on Firebase
        listenForMessages();
        // Load inbox and sent from local storage
        loadInbox();
        loadSent();
        // Start always-listening service
        startVoiceService();
    }

    /** Copy user ID when tapped */
    public void copyId(View v) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ID", myId));
        Toast.makeText(this, "ID copy ho gaya: " + myId, Toast.LENGTH_SHORT).show();
    }

    // ── VOICE SERVICE ──────────────────────────────────────────────────────

    /** Start the always-listening foreground service */
    private void startVoiceService() {
        Intent intent = new Intent(this, VoiceListenService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ── CONTACTS ──────────────────────────────────────────────────────────

    /** Add contact button tapped */
    public void onAddContact(View v) {
        EditText input = findViewById(R.id.addContactInput);
        String id = input.getText().toString().trim().toLowerCase();

        if (id.isEmpty()) {
            Toast.makeText(this, "Contact ka ID dalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (id.equals(myId)) {
            Toast.makeText(this, "Apna ID mat dalo!", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Contact c : contacts) {
            if (c.id.equals(id)) {
                Toast.makeText(this, "Ye contact pehle se hai", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Extract name from ID (part before underscore)
        String namePart = id.contains("_") ? id.split("_")[0] : id;
        String name = namePart.substring(0,1).toUpperCase() + namePart.substring(1);

        contacts.add(new Contact(name, id));
        saveContacts();
        input.setText("");
        renderContacts();
        Toast.makeText(this, name + " add ho gaya!", Toast.LENGTH_SHORT).show();

        // Update hint in service
        updateServiceHint();
    }

    private void loadContacts() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = prefs.getString("contacts", "[]");
        contacts = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                contacts.add(new Contact(o.getString("name"), o.getString("id")));
            }
        } catch (Exception e) { /* fresh start */ }
        renderContacts();
    }

    private void saveContacts() {
        try {
            JSONArray arr = new JSONArray();
            for (Contact c : contacts) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("id", c.id);
                arr.put(o);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("contacts", arr.toString())
                    .apply();
        } catch (Exception e) { /* ignore */ }
    }

    private void renderContacts() {
        if (contactsList == null) return;
        contactsList.removeAllViews();

        if (contacts.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Koi contact nahi. Upar ID daalke add karo.");
            t.setTextColor(0xFF888888);
            t.setPadding(32, 20, 32, 20);
            contactsList.addView(t);
            return;
        }

        for (Contact c : contacts) {
            View row = getLayoutInflater().inflate(R.layout.item_contact, contactsList, false);
            ((TextView) row.findViewById(R.id.contactName)).setText(c.name);
            ((TextView) row.findViewById(R.id.contactId)).setText(c.id);
            row.findViewById(R.id.contactDel).setOnClickListener(v -> {
                contacts.remove(c);
                saveContacts();
                renderContacts();
            });
            contactsList.addView(row);
        }
    }

    private void updateServiceHint() {
        // Tell the service about new contacts
        Intent intent = new Intent(this, VoiceListenService.class);
        intent.setAction("UPDATE_CONTACTS");
        startService(intent);
    }

    // ── FIREBASE INBOX ────────────────────────────────────────────────────

    /** Listen for real-time incoming messages on Firebase */
    private void listenForMessages() {
        String myDbId = myId.replace(".", "_");
        DatabaseReference inboxRef = firebaseDb.child("inbox").child(myDbId);

        inboxRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                // New message received!
                String from     = snap.child("from").getValue(String.class);
                String message  = snap.child("message").getValue(String.class);
                String audio    = snap.child("audio").getValue(String.class);
                Long timestamp  = snap.child("timestamp").getValue(Long.class);
                String fbKey    = snap.getKey();

                if (from == null || message == null) return;

                // Check if already in inbox
                for (Message m : inbox) {
                    if (fbKey.equals(m.fbKey)) return;
                }

                Message msg = new Message(from, message, audio,
                        timestamp != null ? timestamp : System.currentTimeMillis(),
                        fbKey, false);

                inbox.add(0, msg);
                saveInbox();

                // Show on UI thread
                runOnUiThread(() -> {
                    renderInbox();
                    // Show system notification
                    VoiceListenService.showInboxNotification(MainActivity.this, from, message);
                    // Speak it aloud (Text-to-Speech)
                    VoiceListenService.speakMessage(MainActivity.this,
                            from + " ne kaha: " + message);
                    Toast.makeText(MainActivity.this,
                            "📩 " + from + " ne voice note bheja!",
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void loadInbox() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = prefs.getString("inbox", "[]");
        inbox = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                inbox.add(new Message(
                        o.getString("from"),
                        o.getString("message"),
                        o.optString("audio", null),
                        o.getLong("timestamp"),
                        o.getString("fbKey"),
                        o.optBoolean("read", false)
                ));
            }
        } catch (Exception e) { /* fresh */ }
        renderInbox();
    }

    private void saveInbox() {
        try {
            JSONArray arr = new JSONArray();
            // Save last 100 messages
            int limit = Math.min(inbox.size(), 100);
            for (int i = 0; i < limit; i++) {
                Message m = inbox.get(i);
                JSONObject o = new JSONObject();
                o.put("from", m.from);
                o.put("message", m.message);
                if (m.audio != null) o.put("audio", m.audio);
                o.put("timestamp", m.timestamp);
                o.put("fbKey", m.fbKey);
                o.put("read", m.read);
                arr.put(o);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("inbox", arr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    private void renderInbox() {
        if (inboxList == null) return;
        inboxList.removeAllViews();

        long unread = 0;
        for (Message m : inbox) if (!m.read) unread++;
        if (inboxBadge != null)
            inboxBadge.setText(unread > 0 ? " (" + unread + ")" : "");

        if (inbox.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Koi voice note nahi aaya abhi tak");
            t.setTextColor(0xFF888888);
            t.setPadding(32, 40, 32, 40);
            inboxList.addView(t);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a | dd/MM", Locale.getDefault());

        for (Message m : inbox) {
            View row = getLayoutInflater().inflate(R.layout.item_inbox, inboxList, false);
            ((TextView) row.findViewById(R.id.msgFrom)).setText(m.from);
            ((TextView) row.findViewById(R.id.msgPreview)).setText(m.message);
            ((TextView) row.findViewById(R.id.msgTime)).setText(sdf.format(new Date(m.timestamp)));

            if (!m.read) {
                row.setBackgroundColor(0xFF0F1629);
            }

            row.setOnClickListener(v -> {
                m.read = true;
                saveInbox();
                renderInbox();
                // Play audio if exists
                if (m.audio != null) {
                    VoiceListenService.playAudio(this, m.audio);
                } else {
                    // Just speak text
                    VoiceListenService.speakMessage(this, m.from + " ne kaha: " + m.message);
                }
            });
            inboxList.addView(row);
        }
    }

    // ── SENT LOG ──────────────────────────────────────────────────────────

    private void loadSent() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = prefs.getString("sent", "[]");
        sentLog = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                sentLog.add(new SentMessage(
                        o.getString("to"),
                        o.getString("message"),
                        o.getLong("timestamp")
                ));
            }
        } catch (Exception e) { /* fresh */ }
        renderSent();
    }

    private void renderSent() {
        if (sentList == null) return;
        sentList.removeAllViews();

        if (sentLog.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Kuch nahi bheja abhi tak");
            t.setTextColor(0xFF888888);
            t.setPadding(32, 40, 32, 40);
            sentList.addView(t);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a | dd/MM", Locale.getDefault());

        for (SentMessage m : sentLog) {
            View row = getLayoutInflater().inflate(R.layout.item_sent, sentList, false);
            ((TextView) row.findViewById(R.id.sentTo)).setText("→ " + m.to);
            ((TextView) row.findViewById(R.id.sentText)).setText(m.message);
            ((TextView) row.findViewById(R.id.sentTime)).setText(sdf.format(new Date(m.timestamp)));
            sentList.addView(row);
        }
    }

    // ── TABS ──────────────────────────────────────────────────────────────

    public void showTab(View v) {
        String tag = (String) v.getTag();
        findViewById(R.id.tabInbox).setVisibility("inbox".equals(tag) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tabSent).setVisibility("sent".equals(tag) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tabContacts).setVisibility("contacts".equals(tag) ? View.VISIBLE : View.GONE);
    }

    // ── PERMISSIONS ───────────────────────────────────────────────────────

    private void requestAllPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        for (int i = 0; i < perms.length; i++) {
            if (perms[i].equals(Manifest.permission.RECORD_AUDIO)
                    && results[i] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Mic permission chahiye!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── DATA CLASSES ──────────────────────────────────────────────────────

    public static class Contact {
        public String name, id;
        Contact(String name, String id) { this.name = name; this.id = id; }
    }

    public static class Message {
        public String from, message, audio, fbKey;
        public long timestamp;
        public boolean read;
        Message(String from, String message, String audio, long ts, String fbKey, boolean read) {
            this.from = from; this.message = message; this.audio = audio;
            this.timestamp = ts; this.fbKey = fbKey; this.read = read;
        }
    }

    public static class SentMessage {
        public String to, message;
        public long timestamp;
        SentMessage(String to, String msg, long ts) {
            this.to = to; this.message = msg; this.timestamp = ts;
        }
    }
}

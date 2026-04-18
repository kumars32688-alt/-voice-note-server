package com.voicenote.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirebaseHelper.java - Handles cloud backup to Firebase
 *
 * BATTERY OPTIMIZATION STRATEGY:
 * - Only syncs when connected to WiFi (saves mobile data + battery)
 * - Uses anonymous auth (no login screen needed)
 * - Batches sync operations (syncs all unsynced notes at once)
 * - Does NOT use real-time listeners (saves battery)
 *
 * FIREBASE COLLECTIONS:
 * users/{userId}/notes/{noteId}
 */
public class FirebaseHelper {

    private static final String TAG = "FirebaseHelper";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_NOTES = "notes";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;

    public FirebaseHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Sign in anonymously - no email/password needed.
     * Each device gets a unique anonymous user ID.
     * Notes are stored under this ID in Firestore.
     */
    public void signInAnonymously(OnAuthCompleteListener listener) {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            // Already signed in
            Log.d(TAG, "Already signed in: " + currentUser.getUid());
            listener.onSuccess(currentUser.getUid());
            return;
        }

        // Sign in anonymously
        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Anonymous sign-in successful: " + uid);
                    listener.onSuccess(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous sign-in failed", e);
                    listener.onFailure(e.getMessage());
                });
    }

    /**
     * Sync all unsynced notes to Firebase.
     * Only runs when WiFi is connected (battery optimization).
     */
    public void syncUnsyncedNotes(DatabaseHelper dbHelper) {
        // BATTERY OPTIMIZATION: Only sync on WiFi
        if (!isWifiConnected()) {
            Log.d(TAG, "Not on WiFi, skipping sync to save battery");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Not signed in, skipping sync");
            return;
        }

        String userId = user.getUid();

        // Get all notes that haven't been backed up yet
        List<Note> unsyncedNotes = dbHelper.getUnsyncedNotes();

        if (unsyncedNotes.isEmpty()) {
            Log.d(TAG, "All notes already synced!");
            return;
        }

        Log.d(TAG, "Syncing " + unsyncedNotes.size() + " notes...");

        // Upload each unsynced note
        for (Note note : unsyncedNotes) {
            Map<String, Object> noteMap = new HashMap<>();
            noteMap.put("title", note.getTitle());
            noteMap.put("content", note.getContent());
            noteMap.put("timestamp", note.getTimestamp());
            noteMap.put("category", note.getCategory());

            // Save to: users/{userId}/notes/{noteId}
            db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(COLLECTION_NOTES)
                    .document(String.valueOf(note.getId()))
                    .set(noteMap)
                    .addOnSuccessListener(aVoid -> {
                        // Mark as synced in local database
                        dbHelper.markAsSynced(note.getId());
                        Log.d(TAG, "Note synced: " + note.getTitle());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync note: " + note.getTitle(), e);
                    });
        }
    }

    /**
     * Check if the device is connected to WiFi.
     * We only sync on WiFi to save battery and mobile data.
     */
    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null
                && activeNetwork.isConnected()
                && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    /**
     * Check if the user is signed in.
     */
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Get the current user ID (or null if not signed in).
     */
    public String getUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ==================== CALLBACK INTERFACES ====================

    /**
     * Callback for auth operations.
     */
    public interface OnAuthCompleteListener {
        void onSuccess(String userId);
        void onFailure(String error);
    }
}

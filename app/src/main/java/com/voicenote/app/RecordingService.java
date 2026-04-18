package com.voicenote.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * RecordingService.java - Foreground service for voice recording
 *
 * WHY A FOREGROUND SERVICE?
 * - Android kills background apps to save battery
 * - A foreground service shows a notification and keeps running
 * - This is the official Android way to do background recording
 * - It's battery-friendly because Android manages it properly
 *
 * HOW IT WORKS:
 * 1. MainActivity starts this service when user taps "Record"
 * 2. Service shows a notification ("Recording in progress...")
 * 3. Actual speech recognition happens in MainActivity via SpeechRecognizer
 * 4. Service is stopped when recording finishes
 */
public class RecordingService extends Service {

    // Notification channel ID (required for Android 8.0+)
    private static final String CHANNEL_ID = "voice_note_recording";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        // Create notification channel (required for Android 8.0+)
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Build the notification shown during recording
        Notification notification = buildNotification();

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, notification);

        // START_STICKY: restart service if system kills it
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Service is being stopped - recording is done
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't need binding for this service
        return null;
    }

    /**
     * Create a notification channel (required for Android 8.0+).
     * Without this, notifications won't show on newer Android versions.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Recording",
                    NotificationManager.IMPORTANCE_LOW  // LOW = no sound, saves battery
            );
            channel.setDescription("Shows when voice recording is active");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Build the notification shown during recording.
     * Tapping it opens the main app.
     */
    private Notification buildNotification() {
        // When user taps notification, open MainActivity
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Note")
                .setContentText("Recording in progress... Tap to open")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // Can't be swiped away
                .build();
    }
}

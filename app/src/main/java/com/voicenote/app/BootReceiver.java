package com.voicenote.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * BootReceiver - Phone restart hone pe app auto-start karo
 *
 * Jab phone restart hota hai:
 * 1. Android yeh receiver ko call karta hai
 * 2. Hum VoiceListenService start karte hain
 * 3. App bina khole background me sun'na shuru ho jaata hai
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Check karo user setup ho chuka hai
        SharedPreferences prefs = context.getSharedPreferences("VoiceNotePrefs", Context.MODE_PRIVATE);
        String name = prefs.getString("name", "");
        if (name.isEmpty()) return; // Setup nahi hua abhi

        // Service start karo
        Intent serviceIntent = new Intent(context, VoiceListenService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}

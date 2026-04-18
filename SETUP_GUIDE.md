# Voice Note App - Complete Setup Guide

A Hindi + English voice note app for Android with auto-categorization and Firebase cloud backup.

---

## STEP 1: Install Android Studio

1. Download Android Studio from: https://developer.android.com/studio
2. Install it with default settings
3. During setup, install the **Android SDK 34** (Android 14)
4. Wait for all components to download

---

## STEP 2: Open the Project

1. Open Android Studio
2. Click **"Open"** (NOT "New Project")
3. Navigate to this folder: `Desktop/vioce note`
4. Click **OK**
5. Wait for Gradle to sync (may take 2-5 minutes the first time)
6. If asked about Gradle version, click **"Use default gradle wrapper"**

---

## STEP 3: Firebase Setup (for Cloud Backup)

### 3A: Create Firebase Project

1. Go to https://console.firebase.google.com
2. Click **"Add Project"**
3. Name it: `voice-note-app`
4. Disable Google Analytics (not needed) → Click **Create Project**

### 3B: Add Android App to Firebase

1. In the Firebase console, click the **Android icon** to add an app
2. Enter package name: `com.voicenote.app`
3. Enter app nickname: `Voice Note`
4. Click **Register App**

### 3C: Download Config File

1. Download the `google-services.json` file
2. Copy it to: `vioce note/app/google-services.json`
   (It must be in the `app/` folder, NOT the root folder)

### 3D: Enable Anonymous Auth

1. In Firebase Console → **Authentication** → **Sign-in method**
2. Enable **Anonymous** sign-in
3. Click **Save**

### 3E: Set Up Firestore Database

1. In Firebase Console → **Firestore Database**
2. Click **Create Database**
3. Select **Start in test mode** (for development)
4. Choose the nearest server location
5. Click **Enable**

### 3F: Firestore Security Rules (for production)

Replace the default rules with:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/write their own notes
    match /users/{userId}/notes/{noteId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

## STEP 4: Enable Hindi Speech Recognition on Your Phone

For the app to recognize Hindi + English mixed speech:

1. Open your phone's **Settings**
2. Go to **System** → **Languages & Input** → **On-screen keyboard**
3. Tap **Google Voice Typing** (or Gboard → Voice typing)
4. Go to **Languages**
5. Make sure both **Hindi** and **English (India)** are selected
6. Tap **Download offline speech recognition** for both languages
   (This allows the app to work without internet!)

---

## STEP 5: Run the App on Your Phone

### Option A: Run via USB Cable

1. On your phone, enable **Developer Options**:
   - Go to Settings → About Phone
   - Tap **Build Number** 7 times
   - Go back to Settings → Developer Options
   - Enable **USB Debugging**
2. Connect your phone via USB cable
3. In Android Studio, select your phone from the device dropdown
4. Click the **green play button** (Run)
5. The app will install and launch on your phone

### Option B: Run on Emulator

1. In Android Studio → **Tools** → **Device Manager**
2. Click **Create Device**
3. Select **Pixel 6** → Click Next
4. Download **API 34** image → Click Next → Finish
5. Click the play button on the emulator
6. Note: Microphone may not work well on emulator. Use a real phone for best results.

---

## STEP 6: Build APK (to share with others)

### Debug APK (for testing):

1. In Android Studio: **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
2. Wait for build to complete
3. Click **"locate"** in the notification that appears
4. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (for Play Store):

1. In Android Studio: **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → Click Next
3. Create a new keystore:
   - Click **Create new...**
   - Choose a location to save the `.jks` file
   - Set passwords (REMEMBER THESE!)
   - Fill in certificate info
   - Click OK
4. Select **release** build type → Click Finish
5. APK will be at: `app/build/outputs/apk/release/app-release.apk`

---

## Project Structure

```
vioce note/
├── build.gradle                    ← Project-level build config
├── settings.gradle                 ← Module settings
├── gradle.properties               ← Gradle settings
├── SETUP_GUIDE.md                  ← This file
│
├── app/
│   ├── build.gradle                ← App dependencies & config
│   ├── google-services.json        ← Firebase config (YOU ADD THIS)
│   │
│   └── src/main/
│       ├── AndroidManifest.xml     ← App permissions & components
│       │
│       ├── java/com/voicenote/app/
│       │   ├── MainActivity.java       ← Main screen (note list + recording)
│       │   ├── NoteDetailActivity.java ← View/edit a note
│       │   ├── Note.java              ← Data model
│       │   ├── NoteAdapter.java       ← List display adapter
│       │   ├── DatabaseHelper.java    ← Local SQLite database
│       │   ├── FirebaseHelper.java    ← Cloud backup to Firebase
│       │   ├── KeywordMatcher.java    ← Hindi+English categorization
│       │   └── RecordingService.java  ← Background recording service
│       │
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml        ← Main screen layout
│           │   ├── activity_note_detail.xml ← Detail screen layout
│           │   └── item_note.xml            ← Note card layout
│           ├── drawable/                    ← Shapes and backgrounds
│           ├── values/
│           │   ├── colors.xml              ← Color palette
│           │   ├── strings.xml             ← App strings
│           │   └── themes.xml              ← App theme
│           └── xml/
│               └── backup_rules.xml        ← Auto-backup rules
```

---

## How the App Works

### Voice Recording Flow:
1. User taps the **mic button**
2. Android's SpeechRecognizer starts listening
3. Live transcript appears as the user speaks
4. When the user stops speaking (or taps again), recording ends
5. The text is auto-categorized using keyword matching
6. Note is saved to local SQLite database
7. If on WiFi, note is backed up to Firebase

### Category Detection (KeywordMatcher):
- Uses simple keyword matching (no AI/ML needed)
- Checks Hindi AND English keywords
- Example: "kal market jaana hai saamaan lena hai" → **Shopping**
- Example: "tomorrow meeting with client at 3pm" → **Work**
- Example: "mummy ka birthday next week" → **Personal**

### Battery Optimizations:
- Recording uses a **foreground service** (Android-managed, battery efficient)
- Firebase sync only happens on **WiFi** (no mobile data drain)
- No continuous listening (only active when user taps record)
- Notification importance set to **LOW** (no sound/vibration)
- RecyclerView for efficient list rendering

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Gradle sync fails | File → Invalidate Caches → Restart |
| "Speech recognition not available" | Install Google app from Play Store |
| Hindi not recognized | Download Hindi language pack in phone settings |
| Firebase errors | Check google-services.json is in app/ folder |
| Permission denied | Uninstall app, reinstall, and grant permissions |
| APK too large | Enable minifyEnabled in app/build.gradle release block |

---

## Supported Languages

The app uses Android's SpeechRecognizer with `hi-IN` locale, which handles:
- Pure Hindi: "kal doctor ke paas jaana hai"
- Pure English: "buy groceries from the market"
- Mixed (Hinglish): "tomorrow market se saamaan lena hai"
- Transliterated Hindi: Automatically handled by Google's engine

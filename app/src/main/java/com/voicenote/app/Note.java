package com.voicenote.app;

/**
 * Note.java - Data model for a single voice note
 *
 * Each note has:
 * - id: unique identifier
 * - title: short title (auto-generated from first few words or keyword)
 * - content: full transcribed text
 * - timestamp: when the note was created
 * - category: auto-detected category (e.g., "Shopping", "Reminder", "General")
 * - isSynced: whether it's backed up to Firebase
 */
public class Note {

    private long id;
    private String title;
    private String content;
    private long timestamp;
    private String category;
    private boolean isSynced;

    // Empty constructor (needed for Firebase)
    public Note() {}

    // Constructor with all fields
    public Note(long id, String title, String content, long timestamp, String category, boolean isSynced) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.category = category;
        this.isSynced = isSynced;
    }

    // --- Getters and Setters ---

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}

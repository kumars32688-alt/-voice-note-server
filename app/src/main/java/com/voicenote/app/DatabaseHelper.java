package com.voicenote.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHelper.java - Local SQLite database for storing notes
 *
 * WHY SQLite (not just Firebase)?
 * - Works offline (no internet needed)
 * - Faster for reading/writing
 * - Saves battery (no network calls for local operations)
 * - Firebase is used only for cloud backup
 *
 * TABLE STRUCTURE:
 * notes (
 *   id        INTEGER PRIMARY KEY,
 *   title     TEXT,
 *   content   TEXT,
 *   timestamp INTEGER,
 *   category  TEXT,
 *   is_synced INTEGER (0 = not synced, 1 = synced to Firebase)
 * )
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Database info
    private static final String DATABASE_NAME = "voicenotes.db";
    private static final int DATABASE_VERSION = 1;

    // Table and column names
    private static final String TABLE_NOTES = "notes";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_CATEGORY = "category";
    private static final String COL_IS_SYNCED = "is_synced";

    // Singleton instance (only one database connection needed)
    private static DatabaseHelper instance;

    /**
     * Get the single instance of DatabaseHelper.
     * Using singleton pattern to avoid multiple database connections.
     */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the notes table when app is first installed
        String createTable = "CREATE TABLE " + TABLE_NOTES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT, "
                + COL_CONTENT + " TEXT, "
                + COL_TIMESTAMP + " INTEGER, "
                + COL_CATEGORY + " TEXT, "
                + COL_IS_SYNCED + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple upgrade - drop and recreate
        // In a production app, you'd use ALTER TABLE to preserve data
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }

    // ==================== CRUD OPERATIONS ====================

    /**
     * Save a new note to the database.
     * Returns the ID of the newly inserted note.
     */
    public long insertNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TITLE, note.getTitle());
        values.put(COL_CONTENT, note.getContent());
        values.put(COL_TIMESTAMP, note.getTimestamp());
        values.put(COL_CATEGORY, note.getCategory());
        values.put(COL_IS_SYNCED, note.isSynced() ? 1 : 0);

        long id = db.insert(TABLE_NOTES, null, values);
        return id;
    }

    /**
     * Get all notes, sorted by newest first.
     */
    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Query all notes, newest first
        Cursor cursor = db.query(
                TABLE_NOTES,
                null,           // all columns
                null,           // no WHERE clause
                null,           // no WHERE args
                null,           // no GROUP BY
                null,           // no HAVING
                COL_TIMESTAMP + " DESC"  // ORDER BY timestamp descending
        );

        // Loop through results and create Note objects
        if (cursor.moveToFirst()) {
            do {
                Note note = cursorToNote(cursor);
                notes.add(note);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return notes;
    }

    /**
     * Get notes filtered by category.
     */
    public List<Note> getNotesByCategory(String category) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_NOTES,
                null,
                COL_CATEGORY + " = ?",
                new String[]{category},
                null, null,
                COL_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                notes.add(cursorToNote(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return notes;
    }

    /**
     * Search notes by keyword in title or content.
     */
    public List<Note> searchNotes(String query) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String searchPattern = "%" + query + "%";
        Cursor cursor = db.query(
                TABLE_NOTES,
                null,
                COL_TITLE + " LIKE ? OR " + COL_CONTENT + " LIKE ?",
                new String[]{searchPattern, searchPattern},
                null, null,
                COL_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                notes.add(cursorToNote(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return notes;
    }

    /**
     * Get a single note by its ID.
     */
    public Note getNoteById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_NOTES,
                null,
                COL_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null
        );

        Note note = null;
        if (cursor.moveToFirst()) {
            note = cursorToNote(cursor);
        }
        cursor.close();

        return note;
    }

    /**
     * Update an existing note.
     */
    public void updateNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TITLE, note.getTitle());
        values.put(COL_CONTENT, note.getContent());
        values.put(COL_CATEGORY, note.getCategory());
        values.put(COL_IS_SYNCED, note.isSynced() ? 1 : 0);

        db.update(TABLE_NOTES, values, COL_ID + " = ?",
                new String[]{String.valueOf(note.getId())});
    }

    /**
     * Delete a note by its ID.
     */
    public void deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOTES, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    /**
     * Get all notes that haven't been synced to Firebase yet.
     */
    public List<Note> getUnsyncedNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_NOTES,
                null,
                COL_IS_SYNCED + " = 0",
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                notes.add(cursorToNote(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return notes;
    }

    /**
     * Mark a note as synced to Firebase.
     */
    public void markAsSynced(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_SYNCED, 1);
        db.update(TABLE_NOTES, values, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    // ==================== HELPER ====================

    /**
     * Convert a database cursor row to a Note object.
     */
    private Note cursorToNote(Cursor cursor) {
        return new Note(
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
        );
    }
}

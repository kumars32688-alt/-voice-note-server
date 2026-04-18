package com.voicenote.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * NoteDetailActivity.java - View and edit a single note
 *
 * Features:
 * - Shows the full transcribed text
 * - Shows category and timestamp
 * - Allows editing the text
 * - Save button to update the note
 */
public class NoteDetailActivity extends AppCompatActivity {

    private EditText editContent;
    private TextView textCategory;
    private TextView textTimestamp;
    private DatabaseHelper dbHelper;
    private Note currentNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        // Initialize views
        editContent = findViewById(R.id.edit_content);
        textCategory = findViewById(R.id.text_detail_category);
        textTimestamp = findViewById(R.id.text_detail_timestamp);
        FloatingActionButton saveButton = findViewById(R.id.fab_save);

        // Get database helper
        dbHelper = DatabaseHelper.getInstance(this);

        // Get note ID from intent
        long noteId = getIntent().getLongExtra("note_id", -1);
        if (noteId == -1) {
            Toast.makeText(this, "Error: Note not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load the note from database
        currentNote = dbHelper.getNoteById(noteId);
        if (currentNote == null) {
            Toast.makeText(this, "Error: Note not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Display note data
        editContent.setText(currentNote.getContent());
        textCategory.setText("Category: " + currentNote.getCategory());

        SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        textTimestamp.setText(dateFormat.format(new Date(currentNote.getTimestamp())));

        // Set up toolbar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentNote.getTitle());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Save button - update the note
        saveButton.setOnClickListener(v -> saveNote());
    }

    /**
     * Save the edited note back to the database.
     * Also re-detects the category based on updated content.
     */
    private void saveNote() {
        String updatedContent = editContent.getText().toString().trim();

        if (updatedContent.isEmpty()) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update the note
        currentNote.setContent(updatedContent);
        currentNote.setTitle(KeywordMatcher.generateTitle(updatedContent));
        currentNote.setCategory(KeywordMatcher.detectCategory(updatedContent));
        currentNote.setSynced(false);  // Mark for re-sync since content changed

        // Save to database
        dbHelper.updateNote(currentNote);

        Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show();
        finish();  // Go back to main screen
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handle toolbar back button
        finish();
        return true;
    }
}

package com.voicenote.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NoteAdapter.java - Displays notes in a RecyclerView list
 *
 * Each note card shows:
 * - Category icon (color-coded)
 * - Title (first few words of the note)
 * - Preview of content
 * - Timestamp
 * - Category label
 * - Sync status icon
 *
 * Tapping a note opens it in NoteDetailActivity.
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<Note> notes;
    private final OnNoteClickListener listener;

    // Date format for displaying timestamps
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    /**
     * Interface for handling note clicks.
     * MainActivity implements this to open NoteDetailActivity.
     */
    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onNoteLongClick(Note note);
    }

    public NoteAdapter(List<Note> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the card layout for each note
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);

        // Set title and content preview
        holder.titleText.setText(note.getTitle());
        holder.contentText.setText(note.getContent());

        // Format and set timestamp
        String formattedDate = dateFormat.format(new Date(note.getTimestamp()));
        holder.timestampText.setText(formattedDate);

        // Set category label
        holder.categoryText.setText(note.getCategory());

        // Set category color based on type
        int categoryColor = getCategoryColor(note.getCategory());
        holder.categoryIndicator.setBackgroundColor(categoryColor);

        // Show sync status
        holder.syncIcon.setVisibility(note.isSynced() ? View.VISIBLE : View.GONE);

        // Handle clicks
        holder.itemView.setOnClickListener(v -> listener.onNoteClick(note));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onNoteLongClick(note);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    /**
     * Update the list of notes (used after search or filter).
     */
    public void updateNotes(List<Note> newNotes) {
        this.notes = newNotes;
        notifyDataSetChanged();
    }

    /**
     * Get a color for each category (for the side indicator bar).
     */
    private int getCategoryColor(String category) {
        switch (category) {
            case "Shopping":  return 0xFF4CAF50;  // Green
            case "Reminder":  return 0xFFFF9800;  // Orange
            case "Work":      return 0xFF2196F3;  // Blue
            case "Personal":  return 0xFF9C27B0;  // Purple
            case "Finance":   return 0xFFF44336;  // Red
            case "Health":    return 0xFF00BCD4;  // Teal
            case "Food":      return 0xFFFF5722;  // Deep Orange
            default:          return 0xFF607D8B;  // Grey (General)
        }
    }

    /**
     * ViewHolder - holds references to the views in each note card.
     * This avoids calling findViewById repeatedly (performance optimization).
     */
    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView contentText;
        TextView timestampText;
        TextView categoryText;
        View categoryIndicator;
        ImageView syncIcon;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_title);
            contentText = itemView.findViewById(R.id.text_content);
            timestampText = itemView.findViewById(R.id.text_timestamp);
            categoryText = itemView.findViewById(R.id.text_category);
            categoryIndicator = itemView.findViewById(R.id.category_indicator);
            syncIcon = itemView.findViewById(R.id.icon_sync);
        }
    }
}

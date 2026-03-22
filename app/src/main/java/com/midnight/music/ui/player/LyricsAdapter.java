package com.midnight.music.ui.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.utils.LrcParser;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying synchronized lyrics.
 * Highlights the currently active lyric line with a larger, bold, white style.
 * Other lines are shown dimmed and smaller.
 */
public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricViewHolder> {
    private List<LrcParser.LyricLine> lyrics = new ArrayList<>();
    private int activeIndex = -1;

    public void setLyrics(List<LrcParser.LyricLine> newLyrics) {
        this.lyrics = newLyrics != null ? newLyrics : new ArrayList<>();
        this.activeIndex = -1;
        notifyDataSetChanged();
    }

    /**
     * Updates the active line index and refreshes only the changed items.
     * @return the new active index
     */
    public int setActiveIndex(int newIndex) {
        if (newIndex == activeIndex) return activeIndex;

        int oldIndex = activeIndex;
        activeIndex = newIndex;

        if (oldIndex >= 0 && oldIndex < lyrics.size()) {
            notifyItemChanged(oldIndex);
        }
        if (newIndex >= 0 && newIndex < lyrics.size()) {
            notifyItemChanged(newIndex);
        }
        return activeIndex;
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lyric_line, parent, false);
        return new LyricViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        LrcParser.LyricLine line = lyrics.get(position);
        holder.txtLyric.setText(line.getText());

        boolean isActive = (position == activeIndex);
        holder.txtLyric.setAlpha(isActive ? 1.0f : 0.4f);
        holder.txtLyric.setTextSize(isActive ? 26f : 18f);
        holder.txtLyric.setTypeface(
                androidx.core.content.res.ResourcesCompat.getFont(
                        holder.itemView.getContext(),
                        isActive ? R.font.poppins_bold : R.font.poppins_medium));
    }

    @Override
    public int getItemCount() {
        return lyrics.size();
    }

    static class LyricViewHolder extends RecyclerView.ViewHolder {
        final TextView txtLyric;

        LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLyric = itemView.findViewById(R.id.txtLyricLine);
        }
    }
}

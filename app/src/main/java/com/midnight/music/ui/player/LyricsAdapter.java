package com.midnight.music.ui.player;

import android.content.res.ColorStateList;
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
 * Modern RecyclerView adapter for displaying synchronized karaoke lyrics.
 * Highlights the active lyric line with 100% white opacity and an accent pill indicator,
 * keeping font metrics stable to ensure jitter-free, smooth scrolling.
 */
public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricViewHolder> {
    public interface OnLyricClickListener {
        void onLyricClick(LrcParser.LyricLine line);
    }

    private List<LrcParser.LyricLine> lyrics = new ArrayList<>();
    private int activeIndex = -1;
    private int accentColor = 0;
    private OnLyricClickListener onLyricClickListener;

    public LyricsAdapter() {}

    public LyricsAdapter(OnLyricClickListener onLyricClickListener) {
        this.onLyricClickListener = onLyricClickListener;
    }

    public void setOnLyricClickListener(OnLyricClickListener onLyricClickListener) {
        this.onLyricClickListener = onLyricClickListener;
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        notifyDataSetChanged();
    }

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
        if (activeIndex == -1) {
            holder.txtLyric.setAlpha(0.92f);
            if (holder.indicator != null) {
                holder.indicator.setVisibility(View.INVISIBLE);
            }
        } else {
            holder.txtLyric.setAlpha(isActive ? 1.0f : 0.28f);
            if (holder.indicator != null) {
                holder.indicator.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);
                if (accentColor != 0) {
                    holder.indicator.setBackgroundTintList(ColorStateList.valueOf(accentColor));
                }
            }
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && onLyricClickListener != null && pos < lyrics.size()) {
                onLyricClickListener.onLyricClick(lyrics.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return lyrics.size();
    }

    static class LyricViewHolder extends RecyclerView.ViewHolder {
        final TextView txtLyric;
        final View indicator;

        LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLyric = itemView.findViewById(R.id.txtLyricLine);
            indicator = itemView.findViewById(R.id.lyric_indicator);
        }
    }
}

package com.example.midnightmusic.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.example.midnightmusic.R;
import com.example.midnightmusic.models.Genre;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.GenreViewHolder> {
    private final List<Genre> genres;
    private final OnGenreClickListener listener;

    public interface OnGenreClickListener {
        void onGenreClick(Genre genre);
    }

    public GenreAdapter(List<Genre> genres, OnGenreClickListener listener) {
        this.genres = genres;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GenreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_genre_tile, parent, false);
        return new GenreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GenreViewHolder holder, int position) {
        Genre genre = genres.get(position);
        holder.bind(genre);
    }

    @Override
    public int getItemCount() {
        return genres.size();
    }

    class GenreViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView genreName;
        private final ImageView genreImage;

        GenreViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            genreName = itemView.findViewById(R.id.genre_name);
            genreImage = itemView.findViewById(R.id.genre_image);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onGenreClick(genres.get(position));
                }
            });
        }

        void bind(Genre genre) {
            genreName.setText(genre.getName());
            cardView.setCardBackgroundColor(genre.getBackgroundColor());
            
            if (genre.getImageUrl() != null) {
                Picasso.get()
                    .load(genre.getImageUrl().replace("http://", "https://"))
                    .into(genreImage);
            }
        }
    }
} 
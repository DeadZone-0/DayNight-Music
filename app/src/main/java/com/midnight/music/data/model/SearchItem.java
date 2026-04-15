package com.midnight.music.data.model;

import java.util.ArrayList;
import java.util.List;

public class SearchItem {
    public enum Type {
        SONG,
        ARTIST,
        SECTION_HEADER
    }

    private final Type type;
    private Song song;
    private Artist artist;
    private String sectionTitle;

    private SearchItem(Type type) {
        this.type = type;
    }

    public static SearchItem song(Song song) {
        SearchItem item = new SearchItem(Type.SONG);
        item.song = song;
        return item;
    }

    public static SearchItem artist(Artist artist) {
        SearchItem item = new SearchItem(Type.ARTIST);
        item.artist = artist;
        return item;
    }

    public static SearchItem sectionHeader(String title) {
        SearchItem item = new SearchItem(Type.SECTION_HEADER);
        item.sectionTitle = title;
        return item;
    }

    public Type getType() { return type; }
    public Song getSong() { return song; }
    public Artist getArtist() { return artist; }
    public String getSectionTitle() { return sectionTitle; }

    public static List<SearchItem> buildSearchResults(List<Song> songs) {
        List<SearchItem> items = new ArrayList<>();

        if (songs != null && !songs.isEmpty()) {
            for (Song song : songs) {
                items.add(song(song));
            }
        }

        return items;
    }
}
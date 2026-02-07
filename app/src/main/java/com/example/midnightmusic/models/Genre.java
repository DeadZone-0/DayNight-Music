package com.example.midnightmusic.models;

public class Genre {
    private String id;
    private String name;
    private String imageUrl;
    private int backgroundColor;

    public Genre(String id, String name, String imageUrl, int backgroundColor) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.backgroundColor = backgroundColor;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
} 
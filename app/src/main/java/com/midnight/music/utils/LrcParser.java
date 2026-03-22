package com.midnight.music.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC-format synchronized lyrics into a list of LyricLine objects.
 * LRC format: [mm:ss.xx] lyric text
 */
public class LrcParser {

    /**
     * Represents a single line of lyrics with its timestamp.
     */
    public static class LyricLine implements Comparable<LyricLine> {
        private final long timeMs;    // timestamp in milliseconds
        private final String text;    // lyric text

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        public long getTimeMs() { return timeMs; }
        public String getText() { return text; }

        @Override
        public int compareTo(LyricLine other) {
            return Long.compare(this.timeMs, other.timeMs);
        }
    }

    // Matches [mm:ss.xx] or [mm:ss.xxx] timestamps
    private static final Pattern LRC_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{2,3}))?](.*)");

    /**
     * Parses an LRC-format string into a sorted list of LyricLine objects.
     *
     * @param lrc the raw LRC string (lines separated by \n)
     * @return sorted list of LyricLine objects, or empty list if null/invalid
     */
    public static List<LyricLine> parse(String lrc) {
        List<LyricLine> lines = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) return lines;

        String[] rawLines = lrc.split("\n");
        for (String rawLine : rawLines) {
            rawLine = rawLine.trim();
            if (rawLine.isEmpty()) continue;

            Matcher matcher = LRC_PATTERN.matcher(rawLine);
            if (matcher.matches()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                int millis = 0;
                String msStr = matcher.group(3);
                if (msStr != null) {
                    if (msStr.length() == 2) {
                        millis = Integer.parseInt(msStr) * 10; // convert centiseconds to ms
                    } else {
                        millis = Integer.parseInt(msStr);
                    }
                }

                long timeMs = (minutes * 60L + seconds) * 1000L + millis;
                String text = matcher.group(4).trim();

                // Only add non-empty lyric lines
                if (!text.isEmpty()) {
                    lines.add(new LyricLine(timeMs, text));
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    /**
     * Returns the index of the current lyric line based on playback position.
     *
     * @param lines    sorted list of lyric lines
     * @param positionMs current playback position in milliseconds
     * @return index of the active line, or -1 if before the first line
     */
    public static int findActiveLine(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;

        int activeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTimeMs() <= positionMs) {
                activeIndex = i;
            } else {
                break;
            }
        }
        return activeIndex;
    }
}

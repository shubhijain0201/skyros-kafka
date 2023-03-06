package io.util;

public class Pair {
    private String key;

    private String value;

    public Pair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Pair(String value) {
        this.key = null;
        this.value = value;
    }
}

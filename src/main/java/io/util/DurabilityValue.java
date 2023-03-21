package io.util;

public class DurabilityValue {

    private final String message;

    private final boolean parseKey;

    private final String keySeparator;

    public DurabilityValue(String message, boolean parseKey, String keySeparator) {
        this.message = message;
        this.parseKey = parseKey;
        this.keySeparator = keySeparator;
    }

}

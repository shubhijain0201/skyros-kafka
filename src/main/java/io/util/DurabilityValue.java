package io.util;

public class DurabilityValue {

    public final String message;

    private final boolean parseKey;

    private final String keySeparator;

    private final String topic;

    public DurabilityValue(String message, boolean parseKey, String keySeparator, String topic) {
        this.message = message;
        this.parseKey = parseKey;
        this.keySeparator = keySeparator;
        this.topic = topic;
    }

    public String getMessage() {
        return message;
    }

    public boolean isParseKey() {
        return parseKey;
    }

    public String getKeySeparator() {
        return keySeparator;
    }

    public String getTopic() {
        return topic;
    }
}

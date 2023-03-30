package io.util;

public class ClientPutRequest {

    private String message;
    private final boolean parseKey;
    private final String keySeparator;
    private final long clientId;
    private long requestId;
    private String opType;
    private String topic;

    public ClientPutRequest(long clientId, boolean parseKey, String keySeparator, String opType, String topic) {
        this.clientId = clientId;
        this.parseKey = parseKey;
        this.opType = opType;
        this.topic = topic;
        this.keySeparator = keySeparator;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
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

    public long getClientId() {
        return clientId;
    }

    public long getRequestId() {
        return requestId;
    }

    public String getOpType() {
        return opType;
    }

    public String getTopic() {
        return topic;
    }
}

package io.util;

public class ClientRequest {

    private String message;
    private final boolean parseKey;
    private final String keySeparator;
    private final long clientId;
    private long requestId;
    private String opType;

    public ClientRequest(long clientId, boolean parseKey, String keySeparator, String opType) {
        this.clientId = clientId;
        this.parseKey = parseKey;
        this.opType = opType;

        if(parseKey) {
            this.keySeparator = keySeparator;
        } else {
            this.keySeparator = "";
        }
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
}

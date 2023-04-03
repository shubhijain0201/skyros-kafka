package io.util;

public class DurabilityKey {
    private final long clientId;
    private final long requestId;
    public DurabilityKey(long clientId, long requestId) {
        this.clientId = clientId;
        this.requestId = requestId;
    }

    public long getClientId() {
        return clientId;
    }

    public long getRequestId() {
        return requestId;
    }
}

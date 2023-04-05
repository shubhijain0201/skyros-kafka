package io.util;

public class DurabilityKey {

  private final long clientId;
  private final long requestId;
  private static int index = 0;

  public DurabilityKey(long clientId, long requestId) {
    this.clientId = clientId;
    this.requestId = requestId;

    index = index + 1;
  }

  public int getIndex() {
    return index;
  }

  public long getClientId() {
    return clientId;
  }

  public long getRequestId() {
    return requestId;
  }
}

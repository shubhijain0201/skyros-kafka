package io.util;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DurabilityKey that = (DurabilityKey) o;
    return clientId == that.clientId && requestId == that.requestId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, requestId);
  }
}

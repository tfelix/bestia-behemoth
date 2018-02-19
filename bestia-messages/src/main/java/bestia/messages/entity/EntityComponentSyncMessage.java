package bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import bestia.messages.EntityJsonMessage;
import bestia.messages.JsonMessage;

import java.io.Serializable;
import java.util.Objects;

/**
 * This message is send if a component has changed and the clients data model
 * should be updated to reflect this change. The component data is added inside
 * the payload field.
 */
public class EntityComponentSyncMessage extends EntityJsonMessage {

  private static final long serialVersionUID = 1L;

  public static final String MESSAGE_ID = "entity.comp";

  @JsonProperty("ct")
  private final String componentName;

  @JsonRawValue
  private final String payload;

  @JsonProperty("l")
  private final int latency;

  /**
   * For Jackson.
   */
  @SuppressWarnings("unused")
  EntityComponentSyncMessage() {
    super(0, 0);
    payload = null;
    componentName = null;
    latency = 0;
  }

  /**
   * @param accId    The account ID receiving this message.
   * @param entityId The entity this message is related to.
   */
  public EntityComponentSyncMessage(long accId, long entityId,
                                    String componentName,
                                    String payload, int latency) {
    super(accId, entityId);

    if (latency < 0) {
      throw new IllegalArgumentException("Latency can not be negative.");
    }

    Objects.requireNonNull(payload);
    this.componentName = componentName;
    this.payload = payload;
    this.latency = latency;
  }

  public String getPayload() {
    return payload;
  }

  public String getComponentName() {
    return componentName;
  }

  public int getLatency() {
    return latency;
  }

  @JsonIgnore
  @Override
  public long getEntityId() {
    return super.getEntityId();
  }

  @Override
  public JsonMessage createNewInstance(long accountId) {
    return createNewInstance(accountId, getLatency());
  }

  public JsonMessage createNewInstance(long accountId, int latency) {
    return new EntityComponentSyncMessage(accountId,
            getEntityId(),
            getComponentName(),
            getPayload(),
            latency);
  }

  @Override
  public String getMessageId() {
    return MESSAGE_ID;
  }

  @Override
  public String toString() {
    return String.format("ComponentSync[payload: %s]", getPayload());
  }
}

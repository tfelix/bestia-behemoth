package net.bestia.messages.entity;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

/**
 * This message is send if a component has changed and the clients data model
 * should be updated to reflect this change. The component data is added inside
 * the payload field.
 */
public class EntityComponentEnvelope extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.comp";

	@JsonProperty("ct")
	private final String componentName;

	@JsonProperty("pl")
	private final Object payload;

	@JsonProperty("l")
	private final int latency;

	/**
	 * For Jackson.
	 */
	EntityComponentEnvelope() {
		super(0, 0);
		payload = null;
		componentName = null;
		latency = 0;
	}

	/**
	 * @param accId
	 *            The account ID receiving this message.
	 * @param entityId
	 *            The entity this message is related to.
	 */
	private EntityComponentEnvelope(long accId, long entityId, String componentName, Object payload, int latency) {
		super(accId, entityId);

		if (latency < 0) {
			throw new IllegalArgumentException("Latency can not be negative.");
		}

		Objects.requireNonNull(payload);
		this.componentName = componentName;
		this.payload = payload;
		this.latency = latency;
	}

	private EntityComponentEnvelope(long accId, Component component, int latency) {
		super(accId, component.getEntityId());

		if (latency < 0) {
			throw new IllegalArgumentException("Latency can not be negative.");
		}

		Objects.requireNonNull(component);
		this.componentName = componentName(component.getClass());
		this.payload = component;
		this.latency = latency;
	}

	/**
	 * Helper for creating names sticking to the standard of Component names.
	 * 
	 * @param clazz
	 *            The class of the component to add to this system.
	 * @return A legal name.
	 */
	public static String componentName(Class<? extends Component> clazz) {
		return clazz.getSimpleName().toUpperCase().replace("COMPONENT", "");
	}

	/**
	 * Helper method to create arbitrary messages for updating components
	 * without using a specific component directly. Instead helper message
	 * classes can be defined.
	 * 
	 * @param entityId
	 *            The entity to which this component should belong.
	 * @param l 
	 * @param componentName
	 *            The name of the component to use. If its a Component class
	 *            then please use the {@link #componentName} static method to
	 *            generate the name.
	 * @param payload
	 *            The payload to attach to this envelope. Must implement
	 *            {@link Serializable}.
	 * @return A sendable envelope.
	 */
	public static EntityComponentEnvelope forPayload(long accountId, long entityId, String componentName, Object payload) {

		Objects.requireNonNull(payload);
		if (!(Serializable.class.isAssignableFrom(payload.getClass()))) {
			throw new IllegalArgumentException("Payload must implement Serializable.");
		}

		return new EntityComponentEnvelope(accountId, entityId, componentName, payload, 0);
	}

	public static EntityComponentEnvelope forComponent(Component component) {
		return new EntityComponentEnvelope(0, component, 0);
	}

	public Object getPayload() {
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
		return new EntityComponentEnvelope(accountId,
				getEntityId(),
				getComponentName(),
				getPayload(),
				getLatency());
	}
	
	public JsonMessage createNewInstance(long accountId, int latency) {
		return new EntityComponentEnvelope(accountId,
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
		return String.format("EntityCompMessage[payload: %s]", getPayload());
	}
}

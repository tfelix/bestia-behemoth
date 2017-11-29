package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.entity.component.Component;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

import java.util.Objects;

/**
 * This message is send if a component has changed and the clients data model
 * should be updated to reflect this change.
 */
public class EntityComponentMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.comp";

	@JsonProperty("ct")
	private final String componentName;

	@JsonProperty("c")
	private final Component component;
	
	@JsonProperty("l")
	private final int latency;

	/**
	 * For Jackson.
	 */
	EntityComponentMessage() {
		super(0, 0);
		component = null;
		componentName = null;
		latency = 0;
	}

	/**
	 * @param accId
	 *            The account ID receiving this message.
	 * @param entityId
	 *            The entity this message is related to.
	 */
	public EntityComponentMessage(long accId, Component component, int latency) {
		super(accId, component.getEntityId());
		
		if(latency < 0) {
			throw new IllegalArgumentException("Latency can not be negative.");
		}

		Objects.requireNonNull(component);
		this.componentName = component.getClass().getSimpleName().toUpperCase().replace("COMPONENT", "");
		this.component = component;
		this.latency = latency;
	}

	public Component getComponent() {
		return component;
	}

	@JsonIgnore
	@Override
	public long getEntityId() {
		return super.getEntityId();
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new EntityComponentMessage(accountId, component, 0);
	}
	
	public JsonMessage createNewInstance(long accountId, int latency) {
		return new EntityComponentMessage(accountId, component, latency);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("EntityCompMessage[c: %s]", getComponent());
	}
}

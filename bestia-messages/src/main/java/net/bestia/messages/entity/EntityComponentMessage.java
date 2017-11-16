package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.entity.component.Component;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

import java.util.Objects;

/**
 * This message is send if a component has changed and the clients data model should be updated
 * to reflect this change.
 */
public class EntityComponentMessage extends EntityJsonMessage {

	public static final String MESSAGE_ID = "entity.comp";

	@JsonProperty("c")
	private final Component component;

	/**
	 * @param accId    The account ID receiving this message.
	 * @param entityId The entity this message is related to.
	 */
	public EntityComponentMessage(long accId, long entityId, Component component) {
		super(accId, entityId);

		this.component = Objects.requireNonNull(component);
	}

	public Component getComponent() {
		return component;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new EntityComponentMessage(accountId, getEntityId(), component);
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

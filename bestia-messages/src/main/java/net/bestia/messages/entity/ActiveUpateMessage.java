package net.bestia.messages.entity;

import java.io.Serializable;
import java.util.Objects;

import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;

/**
 * This message is used to wrap update messages (which must inherit from
 * {@link JsonMessage}) to all active players in sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ActiveUpateMessage implements EntityMessage, Serializable {

	private static final long serialVersionUID = 1L;

	private final JsonMessage updateMessage;
	private final long entityId;

	public ActiveUpateMessage(long entityId, JsonMessage updateMsg) {
		
		this.entityId = entityId;
		this.updateMessage = Objects.requireNonNull(updateMsg);
	}
	
	public long getEntityId() {
		return entityId;
	}

	public JsonMessage getUpdateMessage() {
		return updateMessage;
	}

	@Override
	public String toString() {
		return String.format("ActiveUpdate[senderEntity: %d, msg: %s]", getEntityId(), updateMessage.toString());
	}

	/**
	 * Wraps the given message inside a {@link ActiveUpateMessage}.
	 * 
	 * @param entityId
	 *            The entity ID from which the update originates. Important to
	 *            determine the line of sight.
	 * @param updateMsg
	 *            The update to be send.
	 */
	public static ActiveUpateMessage wrap(long entityId, JsonMessage updateMsg) {
		return new ActiveUpateMessage(entityId, updateMsg);
	}
}

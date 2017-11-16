package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

/**
 * This message is send to the clients if a visible component for the clients
 * was deleted and needs to be removed.
 * 
 * @author Thomas Felix
 *
 */
public class EntityComponentDeleteMessage extends EntityJsonMessage {
	
	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "entity.compDel";
	
	@JsonProperty("cid")
	private final long componentId;

	public EntityComponentDeleteMessage(long accId, long entityId, long componentId) {
		super(accId, entityId);
		
		this.componentId = componentId;
	}
	
	public long getComponentId() {
		return componentId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new EntityComponentDeleteMessage(accountId, getEntityId(), componentId);
	}
}

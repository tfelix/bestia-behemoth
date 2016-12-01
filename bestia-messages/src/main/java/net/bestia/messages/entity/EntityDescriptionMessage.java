package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.entity.Visual;

/**
 * Tells the engine how to visualize a entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityDescriptionMessage extends JsonMessage {
	
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.desc";
	
	@JsonProperty("eid")
	private final long entityId;
	
	@JsonProperty("v")
	private final Visual visual;
	
	public EntityDescriptionMessage() {
		this(0, 0, new Visual());
	}
	
	public EntityDescriptionMessage(long accId, long entityId, Visual visual) {
		super(accId);
		
		this.entityId = entityId;
		this.visual = Objects.requireNonNull(visual);
	}
	
	public Visual getVisual() {
		return visual;
	}
	
	public long getEntityId() {
		return entityId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}

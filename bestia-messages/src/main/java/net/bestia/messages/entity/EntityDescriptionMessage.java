package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.SpriteInfo;

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
	
	@JsonProperty("s")
	private final SpriteInfo spriteInfo;
	
	/**
	 * For Jackson only.
	 */
	public EntityDescriptionMessage() {
		
		this.entityId = 0;
		this.spriteInfo = null;
	}
	
	public EntityDescriptionMessage(long accId, long entityId, SpriteInfo visual) {
		super(accId);
		
		this.entityId = entityId;
		this.spriteInfo = Objects.requireNonNull(visual);
	}

	public SpriteInfo getSpriteInfo() {
		return spriteInfo;
	}
	
	public long getEntityId() {
		return entityId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}

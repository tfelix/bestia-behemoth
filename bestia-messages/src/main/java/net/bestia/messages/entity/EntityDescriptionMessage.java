package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.domain.SpriteInfo;

/**
 * Tells the engine how to visualize a entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityDescriptionMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.desc";

	@JsonProperty("s")
	private final SpriteInfo spriteInfo;

	/**
	 * For Jackson only.
	 */
	protected EntityDescriptionMessage() {

		this.spriteInfo = null;
	}

	public EntityDescriptionMessage(long accId, long entityId, SpriteInfo visual) {
		super(accId, entityId);

		this.spriteInfo = Objects.requireNonNull(visual);
	}

	public SpriteInfo getSpriteInfo() {
		return spriteInfo;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("EntityDescriptionMessage[accId: %d, entityId: %d, visual: %s]", getAccountId(),
				getEntityId(), spriteInfo.toString());
	}

	@Override
	public EntityDescriptionMessage createNewInstance(long accountId) {
		return new EntityDescriptionMessage(accountId, getEntityId(), spriteInfo);
	}
}

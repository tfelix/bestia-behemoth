package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import bestia.model.entity.InteractionType;

/**
 * By sending this message to the client the client is informed how 
 * he will be able to interact with this entity.
 * 
 * @author Thomas Felix
 *
 */
public class EntityInteractionMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.interact";

	@JsonProperty("is")
	private final List<InteractionType> interactions = new ArrayList<>();

	private EntityInteractionMessage() {
		super(0, 0);
	}
	
	public EntityInteractionMessage(long accId, long eid, InteractionType interaction) {
		super(accId, eid);
		
		Objects.requireNonNull(interactions);
		
		this.interactions.add(interaction);
	}

	public EntityInteractionMessage(long accId, long eid, Collection<InteractionType> interactions) {
		super(accId, eid);
		
		Objects.requireNonNull(interactions);
		
		this.interactions.addAll(interactions);
	}

	@Override
	public String toString() {
		return String.format("EntityInteractionMsg[accId: %d, eid: %d, interact: %s]",
				getAccountId(),
				getEntityId(),
				interactions.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public EntityInteractionMessage createNewInstance(long accountId) {
		return new EntityInteractionMessage(getAccountId(), getEntityId(), interactions);
	}
}

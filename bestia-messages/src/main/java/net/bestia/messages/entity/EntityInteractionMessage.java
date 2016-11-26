package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JasonMessage;
import net.bestia.model.entity.InteractionType;

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityInteractionMessage extends JasonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.interact";

	@JsonProperty("eid")
	private final long entityId;

	@JsonProperty("is")
	private final List<InteractionType> interactions = new ArrayList<>();

	public EntityInteractionMessage() {
		entityId = 0;
	}
	
	public EntityInteractionMessage(AccountMessage accMsg, long eid, InteractionType interaction) {
		super(accMsg);
		
		Objects.requireNonNull(interactions);
		
		this.entityId = eid;
		this.interactions.add(interaction);
	}

	public EntityInteractionMessage(AccountMessage accMsg, long eid, Collection<InteractionType> interactions) {
		super(accMsg);
		
		Objects.requireNonNull(interactions);
		
		this.entityId = eid;
		this.interactions.addAll(interactions);
	}

	public long getEntityId() {
		return entityId;
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
}

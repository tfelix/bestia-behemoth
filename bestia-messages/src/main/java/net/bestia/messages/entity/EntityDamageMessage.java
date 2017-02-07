package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.battle.Damage;

/**
 * This message can be used to communicate a received damage to an entity. If
 * more then one damage is returned it will be a multi damage display. Otherwise
 * it is a simple hit.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityDamageMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "entity.damage";

	@JsonProperty("d")
	private List<Damage> damage = new ArrayList<>();

	/**
	 * Std. Ctor for Jackson.
	 */
	public EntityDamageMessage() {

	}

	/**
	 * Ctor.
	 * 
	 * @param uuid
	 *            UUID of the entity receiving this damage.
	 * @param dmg
	 *            The amount of damage to receive.s
	 */
	public EntityDamageMessage(long entityId, Damage dmg) {
		super(entityId);

		Objects.requireNonNull(dmg);

		this.damage.add(dmg);
	}

	/**
	 * Ctor.
	 * 
	 * @param accId
	 *            Account ID to receive this message.
	 * @param entityId
	 *            Entity ID for this damage.
	 * @param dmg
	 *            The received damage.
	 */
	public EntityDamageMessage(long entityId, List<Damage> dmg) {
		super(entityId);

		Objects.requireNonNull(dmg);

		this.damage.addAll(dmg);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("EntityDmgMessage[eid: %d, dmg: %s]", getEntityId(), damage.toString());
	}
}

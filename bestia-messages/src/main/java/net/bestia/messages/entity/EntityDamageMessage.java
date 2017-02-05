package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.battle.Damage;

/**
 * This message can be used to communicate a received damage to an entity. If
 * more then one damage is returned it will be a multi damage display. Otherwise
 * it is a simple hit.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityDamageMessage extends AccountMessage implements MessageId {

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
	public EntityDamageMessage(Damage dmg) {
		if (dmg == null) {
			throw new IllegalArgumentException("Damage can not be null.");
		}

		this.damage.add(dmg);
	}
	
	/**
	 * Ctor.
	 * 
	 * @param uuid
	 *            UUID of the entity receiving this damage.
	 * @param dmg
	 *            The amount of damage to receive.s
	 */
	public EntityDamageMessage(List<Damage> dmg) {
		if (dmg == null) {
			throw new IllegalArgumentException("Damage can not be null.");
		}

		this.damage.addAll(dmg);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

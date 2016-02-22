package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.misc.Damage;

/**
 * This message can be used to communicate a received damage to an entity. If
 * more then one damage is returned it will be a multi damage display. Otherwise
 * it is a simple hit.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityDamageMessage extends Message {

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
	public EntityDamageMessage(long receiverAccId, Damage dmg) {
		super(receiverAccId);
		if (dmg == null) {
			throw new IllegalArgumentException("Damage can not be null.");
		}

		this.damage.add(dmg);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}

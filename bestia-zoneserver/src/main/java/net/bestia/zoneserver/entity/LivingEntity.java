package net.bestia.zoneserver.entity;

import java.util.List;
import java.util.Set;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.entity.traits.Equipable;
import net.bestia.zoneserver.entity.traits.Loadable;

/**
 * <p>
 * Base entity used by the bestia system to represent all game objects inside
 * the "game" or zone-graph. This class represents interactable entities which
 * can be attacked, positioned, seen etc.
 * </p>
 * <p>
 * Derived from this entity there are multiple objects which contains date to be
 * able to interact with each other or to provide functionality to add status
 * effects etc. to it.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class LivingEntity extends ResourceEntity implements Equipable, Loadable {

	private static final long serialVersionUID = 1L;


	@Override
	public StatusPoints getStatusPoints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StatusPoints getOriginalStatusPoints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addStatusEffect(StatusEffect effect) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeStatusEffect(StatusEffect effect) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<StatusEffect> getStatusEffects() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getElement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getOriginalElement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void kill() {
		// TODO Auto-generated method stub

	}

	@Override
	public Damage takeDamage(Damage damage) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Damage checkDamage(Damage damage) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void takeTrueDamage(Damage damage) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canEquip(Item item) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void equip(Item item) {
		// TODO Auto-generated method stub

	}

	@Override
	public void takeOff(Item item) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<EquipmentSlot> getAvailableEquipmentSlots() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public CollisionShape getShape() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setShape(CollisionShape shape) {
		// TODO Auto-generated method stub

	}
}

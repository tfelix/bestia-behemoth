package net.bestia.zoneserver.entity;

import java.util.Set;

import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.entity.InteractionType;
import net.bestia.zoneserver.entity.traits.Interactable;

public class NPCEntity extends LivingEntity {

	private static final long serialVersionUID = 1L;

	public NPCEntity(BaseValues baseValues, BaseValues ivs, BaseValues effortValues, SpriteInfo visual) {
		super(baseValues, ivs, effortValues, visual);
		// TODO Auto-generated constructor stub
	}

	@Override
	public float getMaxWeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getWeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxItemCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getItemCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean addItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean dropItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isVisible() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public Set<InteractionType> getPossibleInteractions(Interactable interacter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<InteractionType> getInteractions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void triggerInteraction(InteractionType type, Interactable interactor) {
		// TODO Auto-generated method stub
		
	}

}

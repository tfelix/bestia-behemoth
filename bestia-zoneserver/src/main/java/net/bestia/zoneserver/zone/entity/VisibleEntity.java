package net.bestia.zoneserver.zone.entity;

import java.util.ArrayList;
import java.util.List;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Sprite;
import net.bestia.model.shape.Collision;
import net.bestia.model.shape.Point;
import net.bestia.zoneserver.zone.entity.traits.Attackable;
import net.bestia.zoneserver.zone.entity.traits.Collidable;
import net.bestia.zoneserver.zone.entity.traits.Locatable;
import net.bestia.zoneserver.zone.entity.traits.Visible;

/**
 * <p>
 * Base entity used by the bestia system to represent all game objects inside
 * the "game" or zone-graph.
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
public class VisibleEntity implements Locatable, Visible, Attackable, Collidable {
	
	/**
	 * Contains all the applied status effects.
	 */
	private List<StatusEffect> statusEffects = new ArrayList<>();

	@Override
	public Sprite getSprite() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isVisible() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Point getPosition() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setPosition(long x, long y) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getLevel() {
		// TODO Auto-generated method stub
		return 0;
	}

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
	public Element getElement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void kill() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collision getCollision() {
		// TODO Auto-generated method stub
		return null;
	}
}

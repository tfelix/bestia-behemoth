package net.bestia.zoneserver.zone.entity;

import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.zone.Position;
import net.bestia.zoneserver.zone.entity.traits.Attackable;
import net.bestia.zoneserver.zone.entity.traits.Collidable;
import net.bestia.zoneserver.zone.entity.traits.Interactable;
import net.bestia.zoneserver.zone.entity.traits.Locatable;
import net.bestia.zoneserver.zone.entity.traits.Visible;
import net.bestia.zoneserver.zone.shape.CollisionShape;

public class PlayerBestiaEntity implements Visible, Attackable, Collidable, Interactable, Locatable {
	
	public PlayerClass getPlayerClass() {
		return PlayerClass.WARRIOR;
	}
	
	public long getAccountId() {
		return 1;
	}

	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CollisionShape getCollision() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StatusPoints getStatusPoints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addStatusEffect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeStatusEffect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void kill() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getLevel() {
		// TODO Auto-generated method stub
		return 1;
	}

}

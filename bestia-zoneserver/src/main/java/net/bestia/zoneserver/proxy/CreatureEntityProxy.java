package net.bestia.zoneserver.proxy;

import com.artemis.World;

import net.bestia.model.domain.Direction;
import net.bestia.model.domain.StatusPoints;

/**
 * Contains methods for calculating the mana regeneration rate if something
 * changes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class CreatureEntityProxy extends EntityProxy {
	
	private Direction headFacing;
	
	public CreatureEntityProxy(World world, int entityID) {
		super(world, entityID);
		
		this.headFacing = Direction.SOUTH;
	}
	
	public Direction getHeadFacing() {
		return headFacing;
	}

	public void setHeadFacing(Direction headFacing) {
		this.headFacing = headFacing;
	}
	
	/**
	 * Returns the maximum item weight the current bestia could carry. Plase
	 * note: only the bestia master will be used to calculate the inventory max
	 * weight.
	 * 
	 * @return
	 */
	public int getMaxItemWeight() {
		final StatusPoints sp = getStatusPoints();
		return 100 + 100 * sp.getAtk() * 3 + getLevel();
	}

	/**
	 * Re-calculates the current HP and mana regeneration rate based on stats.
	 */
	protected void calculateRegenerationRates() {
		final StatusPoints statusPoints = getStatusPoints();
		
		final int level = getLevel();
		final float hpRegen = (statusPoints.getDef() * 4 + statusPoints.getSpDef() * 1.5f + level) / 100.0f;
		
		
		final float manaRegen = (statusPoints.getDef() * 1.5f + statusPoints.getSpDef() * 3 + level) / 100.0f;
		
		statusPoints.setHpRegenerationRate(hpRegen);
		statusPoints.setManaRegenenerationRate(manaRegen);
	}

}

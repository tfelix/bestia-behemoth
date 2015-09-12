package net.bestia.zoneserver.manager;

import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;

public abstract class BestiaManager {

	protected boolean hasChanged = false;

	public abstract StatusPoints getStatusPoints();

	public abstract Location getLocation();
	
	public abstract int getLevel();

	public float getManaRegenerationRate() {
		final StatusPoints statusPoints = getStatusPoints();
		final int level = getLevel();
		final float regen = (statusPoints.getDef() * 2 + statusPoints.getSpDef() * 4 + level / 2) / 100.0f;
		return regen;
	}

	public BestiaManager() {
		
	}

	public boolean hasChanged() {
		return hasChanged;
	}

	public void resetChanged() {
		hasChanged = false;
	}

}
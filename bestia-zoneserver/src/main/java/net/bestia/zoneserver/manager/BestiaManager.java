package net.bestia.zoneserver.manager;

import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;

public abstract class BestiaManager {

	protected boolean hasChanged = false;

	public abstract StatusPoints getStatusPoints();

	public abstract Location getLocation();

	public abstract float getManaRegenerationRate();

	public BestiaManager() {
		
	}

	public boolean hasChanged() {
		return hasChanged;
	}

	public void resetChanged() {
		hasChanged = false;
	}

}
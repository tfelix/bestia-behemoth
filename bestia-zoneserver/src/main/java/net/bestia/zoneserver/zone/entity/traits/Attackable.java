package net.bestia.zoneserver.zone.entity.traits;

import net.bestia.model.domain.StatusPoints;

/**
 * Entities implementing this interface participating in the attacking system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Attackable {
	
	int getLevel();
	
	StatusPoints getStatusPoints();
	
	void addStatusEffect();

	void removeStatusEffect();
	
	void kill();

}

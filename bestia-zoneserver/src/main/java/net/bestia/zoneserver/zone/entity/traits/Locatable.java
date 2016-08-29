package net.bestia.zoneserver.zone.entity.traits;

import net.bestia.model.domain.Position;

public interface Locatable {
	
	Position getPosition();
	
	void setPosition(long x, long y);

}

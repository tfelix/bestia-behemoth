package net.bestia.zoneserver.game.zone.entity;

import java.util.Collection;

import net.bestia.zoneserver.game.zone.Dimension;
import net.bestia.zoneserver.game.zone.Entity;
import net.bestia.zoneserver.game.zone.Point;

/**
 * Specialized container for holding entites indexed via their coordinates in an
 * efficient way to query this information.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface EntityContainer {
	public void insert(Entity entity);
	public void remove(Entity entity);
	public Collection<Entity> getElements(Dimension range);
	public Collection<Entity> getElements(Point p);
	public int count();
	
}

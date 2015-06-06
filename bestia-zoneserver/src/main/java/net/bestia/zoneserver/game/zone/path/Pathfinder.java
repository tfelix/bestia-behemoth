package net.bestia.zoneserver.game.zone.path;

import java.util.List;

import net.bestia.zoneserver.game.zone.Point;
import net.bestia.zoneserver.game.zone.Zone;


/**
 * Interface for path calculation algorithms used by the bestia game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Pathfinder {

	/**
	 * Tries to find a path between start and end Point. If no Path could be
	 * found because it does not exist or the search depth was exhaustet null is
	 * returned.
	 * 
	 * @param start
	 *            Point to start the search.
	 * @param end
	 *            Point to end the search.
	 * @param zone
	 *            Zone interface so the search algorithm can access for example
	 *            walking speed data.
	 * @return List of Points representing the path. Or null if no path could be
	 *         found.
	 */
	List<Point> findPath(Point start, Point end, Zone zone);

}

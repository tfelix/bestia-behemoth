package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;

import net.bestia.model.zone.Point;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Tile;
import net.bestia.zoneserver.zone.shape.Rect;

/**
 * The {@link MapService} is responsible for effectivly quering the cache in
 * order to get map data from the in memory db.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class MapService {
	
	public final static String CACHE_KEY = "tiles";

	private final HazelcastInstance hazelcastInstance;

	@Autowired
	public MapService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
	}

	/**
	 * Retrieves all the needed map data from the database and build a map
	 * object around it in order to access it.
	 * 
	 * @param range
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public Map getMap(Rect range) {
		
		IMap<Point, Tile> tileData = hazelcastInstance.getMap( "tiles" );
		
		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();
		
		Predicate xPredicate = e.get("position.x").between(range.getX(), range.getX() + range.getWidth());
		Predicate yPredicate = e.get("position.y").between(range.getY(), range.getY() + range.getHeight());
		
		final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate);
		
		final Collection<Tile> tiles = tileData.values(rangePredicate);

		// Find the tilesets to this tiles.
		
		// Build the map objects.
		
		return null;
	}

}

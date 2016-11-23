package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;

import net.bestia.model.domain.Tile;
import net.bestia.model.map.Map;
import net.bestia.model.map.Map.MapBuilder;
import net.bestia.model.map.TileProperties;
import net.bestia.model.map.Tileset;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Rect;
import net.bestia.model.shape.Size;

/**
 * The {@link MapService} is responsible for effectively querying the cache in
 * order to get map data from the in memory db since we can not simply load the
 * fully map into memory as a single map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class MapService {

	public final static String CACHE_KEY = "map.tiles";
	public final static String MAP_NAME_KEY = "map.name";
	private final static String TILESET_KEY = "map.tilesets";
	private final static String TILE_PROPS_KEY = "map.tileprops";

	private final HazelcastInstance hazelcastInstance;

	@Autowired
	public MapService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
	}

	/**
	 * Returns the name of the current map/world.
	 * 
	 * @return The name of the map.
	 */
	public String getName() {
		return "Aventerra";
	}

	/**
	 * Returns the name of the area in which this point is lying.
	 * 
	 * @param p
	 *            The coordinates of which the local name should be found.
	 * @return The name of the local area.
	 */
	public String getAreaName(Point p) {
		return "Kalarian";
	}

	public void setName(String name) {
		// TODO das hier implementieren.
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

		final IMap<Point, List<Tile>> tileData = hazelcastInstance.getMap(CACHE_KEY);

		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();

		final Predicate xPredicate = e.get("position.x").between(range.getX(), range.getX() + range.getWidth());
		final Predicate yPredicate = e.get("position.y").between(range.getY(), range.getY() + range.getHeight());

		final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate);

		final Collection<List<Tile>> tiles = tileData.values(rangePredicate);

		// Find the highest map layer.
		final int maxLevel = tiles.parallelStream().map(x -> x.size()).max(Integer::compare).get();

		final Set<Tileset> tilesets = new HashSet<>();

		// Find the tilesets to this tiles.
		for (List<Tile> lt : tiles) {
			for (Tile t : lt) {
				final Tileset ts = getTileset(t.getGid());
				tilesets.add(ts);
			}
		}

		// Build the map objects.
		final MapBuilder mapBuilder = new MapBuilder();

		//mapBuilder.setSize(new Size((int) range.getWidth(), (int) range.getHeight()));
		mapBuilder.addTilesets(tilesets);

		for (int i = 0; i < maxLevel; i++) {
			// Find all tiles on this particular layer.

			// Add the tiles to the mapbuilder.
			// mapBuilder.addTiles(i, tiles);
		}

		return mapBuilder.build();
	}

	/**
	 * Returns a tileset via its name.
	 * 
	 * @param name
	 * @return
	 */
	public Tileset getTileset(String name) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);
		return tilesetData.get(name);
	}

	/**
	 * Returns the tileset depending on the gid of a tile. The system will
	 * perform a lookup in order to find the correct tileset for the given guid.
	 * 
	 * @param gid
	 *            The id of the tile to which the tileset should be found.
	 * @return The found tileset containing the tile with the GID or null if not
	 *         tileset could been found.
	 */
	public Tileset getTileset(int gid) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);

		final Optional<Tileset> ts = tilesetData.values()
				.parallelStream()
				.filter(x -> x.contains(gid))
				.findAny();

		return ts.isPresent() ? ts.get() : null;
	}

	/**
	 * This will save all the tiles as the given layer to the database.
	 * 
	 * @param layer
	 * @param tiles
	 */
	public void saveTiles(int layer, List<Tile> tiles) {

		final IMap<Point, List<Tile>> tileData = hazelcastInstance.getMap(CACHE_KEY);
	}

	/**
	 * Saves the given tileset into the memory cache for later retrieval.
	 * 
	 * @param tileset
	 */
	public void saveTileset(Tileset tileset) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);
		tilesetData.put(tileset.getName(), tileset);
	}

	/**
	 * Saves the given tile properties for this given tile.
	 * 
	 * @param gid Global tile id.
	 * @param tileProps The properties of this tile.
	 */
	public void saveTileProperties(int gid, TileProperties tileProps) {
		final IMap<Integer, TileProperties> props = hazelcastInstance.getMap(TILE_PROPS_KEY);
		props.put(gid, tileProps);
	}
}

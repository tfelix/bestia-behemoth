package net.bestia.zoneserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import net.bestia.model.dao.TileDAO;
import net.bestia.model.domain.Tile;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.MapChunk;
import net.bestia.model.map.Tileset;

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

	private final static String TILESET_KEY = "map.tilesets";

	private final TileDAO tileDao;

	private final HazelcastInstance hazelcastInstance;

	@Autowired
	public MapService(TileDAO tileDao, HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.tileDao = Objects.requireNonNull(tileDao);
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

	/**
	 * Retrieves all the needed map data from the database and build a map
	 * object around it in order to access it.
	 * 
	 * @param range
	 * @return
	 */
	public Map getMap(Rect range) {

		return null;
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
				.stream()
				.filter(x -> x.contains(gid))
				.findAny();

		return ts.isPresent() ? ts.get() : null;
	}

	/**
	 * Returns the chunks of map data given in this list. If the chunks could
	 * not been found an empty list is returned.
	 * 
	 * @param chunkCords
	 * @return
	 */
	public List<MapChunk> getChunks(List<Point> chunkCords) {

		final List<MapChunk> chunks = new ArrayList<>();

		for (Point point : chunkCords) {
			final Rect area = MapChunk.getWoldRect(point);
			// -1 because from to coordinates are on less then height (start to
			// count at the start coordiante).
			List<Tile> tiles = tileDao.getTilesInRange(area.getX(), area.getY(), area.getHeight() - 1,
					area.getWidth() - 1);

			// Now filter the tiles for the ground layer.
			List<Integer> groundTiles = tiles.stream()
					.filter(t -> t.getLayer() == 0)
					.map(Tile::getGid)
					.collect(Collectors.toList());

			// Now filter and group the tiles by layers.
			java.util.Map<Short, List<Tile>> layers = tiles.stream()
					.filter(t -> t.getLayer() != 0)
					.collect(Collectors.groupingBy(Tile::getLayer));

			List<java.util.Map<Point, Integer>> sortedLayers = new ArrayList<>();

			for (Entry<Short, List<Tile>> entry : layers.entrySet()) {

				java.util.Map<Point, Integer> temp = new HashMap<>();

				for (Tile t : entry.getValue()) {
					temp.put(new Point(t.getX(), t.getY()), t.getGid());
				}

				sortedLayers.add(temp);
			}

			chunks.add(new MapChunk(point, groundTiles, sortedLayers));
		}

		return chunks;
	}
}

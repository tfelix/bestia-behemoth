package net.bestia.zoneserver.zone.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bestia.zoneserver.script.MapTriggerScript;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;
import net.bestia.zoneserver.zone.shape.Vector2;
import net.bestia.zoneserver.zone.spawn.Spawner;

/**
 * Representation of a map used by the bestia zone server. The map holds all
 * static map data like tiles, scripts and entities like light sources and
 * sounds.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Map {

	/**
	 * The {@link MapBuilder} should be used to construct a {@link Map}. Must be
	 * filled by a {@link Maploader} and then construct a map out of it.
	 *
	 */
	public static class MapBuilder {

		public int width;
		public int height;
		public String tileset;
		public Set<Vector2> collisions = new HashSet<>();
		public java.util.Map<Vector2, Tile> tiles = new HashMap<>();
		public String mapDbName;
		public List<Script> portals = new ArrayList<>();
		public String globalMapscript = "";
		public List<Spawner> spawns;

		public MapBuilder load(Maploader loader) throws IOException {
			loader.loadMap(this);
			return this;
		}

		public Map build() {
			return new Map(this);
		}
	}

	public static class Script {

		private CollisionShape shape;
		private MapTriggerScript script;

		public Script(MapTriggerScript script, CollisionShape shape) {
			this.shape = shape;
			this.script = script;
		}

		public CollisionShape getShape() {
			return shape;
		}

		public MapTriggerScript getMapScript() {
			return script;
		}
	}

	private String mapDbName;
	private String tileset;
	private Rect dimensions;

	private final String globalMapscript;
	private final List<Script> portals;
	private final List<Spawner> spawns;

	private java.util.Map<Vector2, Tile> tiles = new HashMap<>();

	/**
	 * Constructor must be invoked with a {@link MapBuilder}. All needed data
	 * will be extracted from the builder.
	 * 
	 * @param builder
	 */
	private Map(MapBuilder builder) {
		dimensions = new Rect(0, 0, builder.width, builder.height);
		tileset = builder.tileset;
		mapDbName = builder.mapDbName;
		tiles = Collections.unmodifiableMap(builder.tiles);
		globalMapscript = builder.globalMapscript;
		portals = builder.portals;
		spawns = Collections.unmodifiableList(builder.spawns);
	}

	/**
	 * Checks if a tile is walkable at all.
	 * 
	 * @param cords
	 *            Coordinate to check.
	 * @return TRUE if tile is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(Vector2 cords) {

		if (!dimensions.collide(cords)) {
			return false;
		}

		return tiles.get(cords).isWalkable();
	}

	/**
	 * Returns the walkspeed for a given tile.
	 * 
	 * @param cords
	 *            The coordinates for the tile to get the walkspeed.
	 * @return The walkspeed.
	 */
	public int getWalkspeed(Vector2 cords) {
		if (cords.x > dimensions.getWidth() || cords.y > dimensions.getHeight() || cords.x < 0 || cords.y < 0) {
			return 0;
		}

		return tiles.get(cords).getWalkspeed();
	}

	/**
	 * The map db name.
	 * 
	 * @return The map db name.
	 */
	public String getMapDbName() {
		return mapDbName;
	}

	/**
	 * Returns the width and height of the map in tiles.
	 * 
	 * @return Width of the map.
	 */
	public Rect getDimension() {
		return dimensions;
	}

	/**
	 * Returns the tileset of this map.
	 * 
	 * @return Name of the tileset of this map.
	 */
	public String getTileset() {
		return tileset;
	}

	/**
	 * The mapscripts which are attached to this map.
	 * 
	 * @return A list of names for the mapscripts which are attached to this
	 *         map.
	 */
	public String getGlobalMapscript() {
		return globalMapscript;
	}

	/**
	 * The map portals on this map.
	 * 
	 * @return A list with the parsed map portals.
	 */
	public List<Script> getPortals() {
		return portals;
	}

	/**
	 * Returns the spawn list for mobs on this map.
	 * 
	 * @return The spawn list.
	 */
	public List<Spawner> getSpawnlist() {
		return spawns;
	}

	@Override
	public String toString() {
		return String.format("Map[mapDbName: %s, dim: %s]", mapDbName, dimensions.toString());
	}

}

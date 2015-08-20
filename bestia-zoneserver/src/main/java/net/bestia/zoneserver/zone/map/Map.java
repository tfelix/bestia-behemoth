package net.bestia.zoneserver.zone.map;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.bestia.zoneserver.zone.Rect;
import net.bestia.zoneserver.zone.Vector2;

/**
 * Representation of a map used by the bestia zone server. The map holds all
 * static map data like tiles, scripts and entities like light sources and
 * sounds.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Map {

	public static class MapBuilder {

		int width;
		int height;
		String globalScript;
		String tileset;
		Set<Vector2> collisions = new HashSet<>();
		java.util.Map<Vector2, Tile> tiles = new HashMap<>();
		String mapDbName;
		
		public MapBuilder load(Maploader loader) throws IOException {
			loader.loadMap(this);
			return this;
		}

		public Map build() {
			return new Map(this);
		}
	}

	private String mapDbName;
	private String tileset;
	private Rect dimensions;

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
	}

	/**
	 * Checks if a tile is walkable at all.
	 * 
	 * @param cords
	 *            Coordinate to check.
	 * @return TRUE if tile is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(Vector2 cords) {
		
		if(!dimensions.contains(cords)) {
			return false;
		}

		return tiles.get(cords).isWalkable();
	}

	public int getWalkspeed(Vector2 cords) {
		if (cords.x > dimensions.getWidth() || cords.y > dimensions.getHeight()
				|| cords.x < 0 || cords.y < 0) {
			return 0;
		}

		return tiles.get(cords).getWalkspeed();
	}

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

}
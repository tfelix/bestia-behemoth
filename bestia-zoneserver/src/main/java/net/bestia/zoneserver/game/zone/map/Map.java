package net.bestia.zoneserver.game.zone.map;

import java.util.HashMap;

import net.bestia.zoneserver.game.zone.Dimension;
import net.bestia.zoneserver.game.zone.Point;

public class Map {

	public static class Mapbuilder {

		public int width;
		public int height;
		public String globalScript;
		public String tileset;

		public Map build(Maploader loader) {

			// loader.loadMap(this);
			return new Map(this);
		}
	}

	private String mapDbName;
	private String tileset;
	private Dimension dimensions;
	private java.util.Map<Point, Tile> tiles = new HashMap<>();

	private Map(Mapbuilder builder) {
		dimensions = new Dimension(builder.width, builder.height);
		tileset = builder.tileset;
	}

	/**
	 * Checks if a tile is walkable at all.
	 * 
	 * @param cords
	 *            Coordinate to check.
	 * @return TRUE if tile is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(Point cords) {
		if (cords.x > dimensions.getWidth() || cords.y > dimensions.getHeight()
				|| cords.x < 0 || cords.y < 0) {
			return false;
		}

		return tiles.get(cords).isWalkable();
	}

	public int getWalkspeed(Point cords) {
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
	public Dimension getDimension() {
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

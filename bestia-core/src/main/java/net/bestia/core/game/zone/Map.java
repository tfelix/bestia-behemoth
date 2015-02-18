package net.bestia.core.game.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Map {

	public static class Mapbuilder {

		public int width;
		public int height;

		public Map build(Maploader loader) {

			// loader.loadMap(this);
			return new Map(this);
		}
	}

	private Point dimensions;
	private java.util.Map<Point, Tile> tiles = new HashMap<>();

	public Map(Mapbuilder builder) {
		dimensions = new Point(builder.width, builder.height);
	}

	/**
	 * Checks if a tile is walkable at all.
	 * 
	 * @param cords
	 *            Coordinate to check.
	 * @return TRUE if tile is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(Point cords) {
		if (cords.x > dimensions.x || cords.y > dimensions.y || cords.x < 0 || cords.y < 0) {
			return false;
		}
		
		return tiles.get(cords).isWalkable();
	}
	
	public int getWalkspeed(Point cords) {
		if (cords.x > dimensions.x || cords.y > dimensions.y || cords.x < 0 || cords.y < 0) {
			return 0;
		}
		
		return tiles.get(cords).getWalkspeed();
	}

}

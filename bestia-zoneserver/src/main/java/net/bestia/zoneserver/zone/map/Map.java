package net.bestia.zoneserver.zone.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import net.bestia.model.zone.Point;
import net.bestia.model.zone.Size;
import net.bestia.zoneserver.zone.shape.Rect;

public class Map {

	public static class MapBuilder {

		private String name;
		private List<Tileset> tilesets = new ArrayList<>();
		private List<Tile> tiles = new ArrayList<>();
		private Size size = new Size(1, 1);

		public void setSize(Size size) {
			this.size = size;
		}

		/**
		 * Adds a new tile layers. The layer must have the size of the map which
		 * must be provided first!
		 * 
		 * @param layer
		 */
		public void addTiles(int layer, List<Tile> tiles) {
			tiles.addAll(tiles);
		}

		public void addTileset(Tileset ts) {
			this.tilesets.add(ts);
		}

		/**
		 * Builds the map from the provided data.
		 * 
		 * @return A new build map.
		 */
		public Map build() {
			// Perform some sanity checks the tile buttom layer must have no
			// holes.

			return new Map(this);
		}

	}

	private final String name;
	private final Size size;

	private boolean[][] walkable;
	private java.util.Map<Integer, java.util.Map<Point, Integer>> tileLayer = new HashMap<>();

	public Map() {
		name = "";
		size = new Size(1, 1);
	}

	public Map(MapBuilder builder) {

		this.name = builder.name;
		this.size = builder.size;

		walkable = new boolean[size.getHeight()][size.getWidth()];

	}

	public boolean isWalkable(long x, long y) {
		return isWalkable(new Point(x, y));
	}

	public boolean isWalkable(Point p) {
		return walkable[(int) p.getY()][(int) p.getY()];
	}
}

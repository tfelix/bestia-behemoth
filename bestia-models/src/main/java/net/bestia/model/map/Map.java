package net.bestia.model.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;

/**
 * Helper object to encapsulate map data. Usually the game engine works on this
 * map part objects to provide the pathfinding algorithms with enough
 * information or the AI. Not the whole map is encapsulated inside this map.
 * Usually this is a rather small part because all data is hold in memory and
 * not streamed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Map {

	/**
	 * A helper class which is used in order to construct maps for the bestia
	 * system.
	 * 
	 */
	public static class MapBuilder {

		private final List<Tileset> tilesets = new ArrayList<>();
		private final List<Integer> groundTiles = new ArrayList<>();
		private final List<java.util.Map<Point, Integer>> layerTiles = new ArrayList<>();
		private Rect rect = new Rect(1, 1, 1, 1);

		public void setRect(Rect rect) {
			this.rect = rect;
		}

		/**
		 * Adds a new tile layers. The layer must have the size of the map which
		 * must be also provided!
		 * 
		 * @param layer
		 */
		public void addGroundTiles(List<Integer> tiles) {
			groundTiles.addAll(tiles);
		}

		public void addTileset(Tileset ts) {
			this.tilesets.add(ts);
		}

		/**
		 * Adds a new tile layer to this builder.
		 * 
		 * @param layer
		 *            The layer to be added.
		 * @param tiles
		 *            The new tiles of this layer.
		 */
		public void addTileLayer(int layer, java.util.Map<Point, Integer> tiles) {
			if (layer > layerTiles.size()) {

				for (int i = layerTiles.size(); i < layer; i++) {
					layerTiles.add(new HashMap<>());
				}
			}

			java.util.Map<Point, Integer> tileLayer = layerTiles.get(layer);
			tileLayer.clear();
			tileLayer.putAll(tiles);
		}

		/**
		 * Builds the map from the provided data.
		 * 
		 * @return A new build map.
		 */
		public Map build() {
			// Perform some sanity checks the tile bottom layer must have no
			// holes.
			if (groundTiles.size() != rect.getHeight() * rect.getWidth()) {
				throw new IllegalStateException("Groundlayer tile count does not match map size.");
			}

			return new Map(this);
		}

		/**
		 * Adds multiple tilesets to the builder.
		 * 
		 * @param tilesets
		 */
		public void addTilesets(Collection<Tileset> tilesets) {
			this.tilesets.addAll(tilesets);
		}

	}

	private final Rect rect;
	private final List<Tileset> tilesets;
	private final List<Integer> groundLayer;

	/**
	 * Sparse layers on top of the bottom tiles.
	 */
	private final List<java.util.Map<Point, Integer>> tileLayer = new ArrayList<>();

	/**
	 * Private ctor to be used with the builder.
	 * 
	 * @param builder
	 *            Builder object to fill the data into the map.
	 */
	private Map(MapBuilder builder) {

		this.rect = Objects.requireNonNull(builder.rect);
		Objects.requireNonNull(builder.tilesets);
		this.tilesets = Collections.unmodifiableList(new ArrayList<>(builder.tilesets));
		Objects.requireNonNull(builder.groundTiles);
		this.groundLayer = Collections.unmodifiableList(builder.groundTiles);
	}


	/**
	 * Returns the walkspeed of a given x and y coordiante. The walkspeed will
	 * be 0 if the tile does not exist inside the {@link Map}.
	 * 
	 * @param x
	 *            X coordinate.
	 * @param y
	 *            Y coordinate.
	 * @return The current walkspeed on this tile.
	 */
	public Walkspeed getWalkspeed(long x, long y) {

		// Find the walkspeed on the tile.
		final int gid = getGid(x, y);

		if (gid == 0) {
			return Walkspeed.fromInt(0);
		}

		return getTileset(gid).map(ts -> Walkspeed.fromInt(ts.getProperties(gid).getWalkspeed()))
				.orElse(Walkspeed.fromInt(0));
	}

	/**
	 * Finds the tile id of the given coordiante.
	 * 
	 * @return
	 */
	private int getGid(long x, long y) {
		// Now check the layers.
		final long cx = x - rect.getX();
		final long cy = y - rect.getY();

		final int index = (int) (cy * rect.getWidth() + cx);
		final Integer gid = groundLayer.get(index);

		if (gid == null) {
			return 0;
		}

		return gid;
	}

	/**
	 * Checks if the given point of this map is walkable. If the point is out of
	 * range of the selected map an {@link IndexOutOfBoundsException} is thrown.
	 * The given coordinates must be in world space. The coordinates are not
	 * relative to this map part.
	 * 
	 * @param p
	 *            The point to check walkability.
	 * @return TRUE if the point is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(Point p) {

		if (!rect.collide(p)) {
			throw new IndexOutOfBoundsException("X or/and Y does not lie inside the map rectangle.");
		}

		final int gid = getGid(p.getX(), p.getY());

		if (gid == 0) {
			return false;
		}

		final boolean groundWalkable = getTileset(gid).map(ts -> ts.getProperties(gid).isWalkable()).orElse(false);

		if (!groundWalkable) {
			return false;
		}

		// Now we must check the layers above it.
		boolean layerWalkable = tileLayer.stream().filter(d -> d.containsKey(p)).map(d -> d.get(p)).filter(layerGid -> {
			return getTileset(layerGid).map(ts -> ts.getProperties(gid).isWalkable()).orElse(true);
		}).findAny().map(data -> false).orElse(true);

		return layerWalkable;
	}

	/**
	 * See {@link #isWalkable(Point)}.
	 * 
	 * @param x
	 *            Position coordiante X
	 * @param y
	 *            Position coordinate Y
	 * @return TRUE if the point is walkable. FALSE otherwise.
	 */
	public boolean isWalkable(long x, long y) {
		return isWalkable(new Point(x, y));
	}

	/**
	 * Returns TRUE if the given tile blocks the sight of the player. FALSE if
	 * the player can look over the tile.
	 * 
	 * @param x
	 *            X coordinate to check.
	 * @param y
	 *            Y coordinate to check.
	 * @return TRUE if the tile blocks the sight. FALSE otherwise.
	 */
	public boolean blocksSight(long x, long y) {

		final Point p = new Point(x, y);

		if (!rect.collide(p)) {
			throw new IndexOutOfBoundsException("X or/and Y does not lie inside the map rectangle.");
		}

		final int gid = getGid(x, y);

		if (gid == 0) {
			return false;
		}

		final boolean groundBlockSight = getTileset(gid).map(ts -> ts.getProperties(gid).blockSight()).orElse(false);

		if (!groundBlockSight) {
			return false;
		}

		// Now we must check the layers above it.
		final boolean blocksSight = tileLayer.stream()
				.filter(layer -> layer.containsKey(p))
				.map(layer -> layer.get(p))
				.map(this::getTileset)
				.anyMatch(tileset -> {
					return tileset.isPresent() && tileset.get().getProperties(gid).blockSight();
				});
				
		return blocksSight;
	}

	/**
	 * Gets the tilset for the given gid.
	 * 
	 * @param gid
	 * @return The tileset.
	 */
	private Optional<Tileset> getTileset(int gid) {

		return tilesets.stream().filter(ts -> ts.contains(gid)).findAny();
	}


	/**
	 * Returns the size (and location) of map which usually only represents a
	 * small part of the whole global map.
	 * 
	 * @return The view to this part of the map.
	 */
	public Rect getRect() {
		return rect;
	}
}

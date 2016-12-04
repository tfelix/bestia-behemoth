package net.bestia.model.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.domain.Tile;
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

	private static final Logger LOG = LoggerFactory.getLogger(Map.class);

	/**
	 * Max sight range in tiles in every direction.
	 */
	public static int SIGHT_RANGE = 32;

	/**
	 * A helper class which is used in order to construct maps for the bestia
	 * system.
	 * 
	 */
	public static class MapBuilder {

		private final List<Tileset> tilesets = new ArrayList<>();
		private final List<Tile> groundTiles = new ArrayList<>();
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
		public void addGroundTiles(List<Tile> tiles) {
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
	private final List<java.util.Map<Point, Integer>> tileLayer = new ArrayList<>();

	public Map(MapBuilder builder) {

		this.rect = Objects.requireNonNull(builder.rect);
		Objects.requireNonNull(builder.tilesets);
		this.tilesets = Collections.unmodifiableList(new ArrayList<>(builder.tilesets));
		Objects.requireNonNull(builder.groundTiles);
		this.groundLayer = Collections.unmodifiableList(builder.groundTiles.stream()
				.map(x -> x.getGid())
				.collect(Collectors.toList()));
	}

	public boolean isWalkable(long x, long y) {
		return isWalkable(new Point(x, y));
	}

	public boolean isWalkable(Point p) {
		if(!rect.collide(p)) {
			throw new IndexOutOfBoundsException("Coodination does not lie inside this map.");
		}
		
		// Now check the layers.
		long x = p.getX() - rect.getOrigin().getX();
		long y = p.getY() - rect.getOrigin().getY();
		int index = (int) (y * rect.getWidth() + x);
		final int gid = groundLayer.get(index);
		
		boolean groundWalkable = getTileset(gid)
				.map(ts -> ts.getProperties(gid).isWalkable())
				.orElse(false);
		
		if(!groundWalkable) {
			return false;
		}
		
		// TODO Check the sparse map.

		return true;
	}

	private Optional<Tileset> getTileset(int gid) {
		final Optional<Tileset> tileset = tilesets.stream()
				.filter(ts -> ts.contains(gid))
				.findAny();

		if (!tileset.isPresent()) {
			LOG.warn("Gid {} not found inside tilsets.", gid);
			return null;
		}

		return tileset;
	}

	/**
	 * Returns the sparse layer map of non walkable tiles. Note: Only the non
	 * walkable tiles are listed in this map. If a point is not inside this map
	 * it means it is walkable. This should be the majority of points. The
	 * points are the global coordinates.
	 * 
	 * @return The sparse collisions layer.
	 */
	/*
	 * public java.util.Map<Point, Boolean> getSparseCollisionLayer() {
	 * 
	 * final java.util.Map<Point, Boolean> collisions = new HashMap<>();
	 * 
	 * int y = (int) rect.getY(); /* for (boolean[] row : walkable) { int x =
	 * (int) rect.getX(); for (boolean walkable : row) {
	 * 
	 * if (!walkable) { collisions.put(new Point(x, y), false); }
	 * 
	 * x++; } y++; }
	 */
	/*
	 * return collisions; }
	 */

	/**
	 * Gets the linear tile ids for the ground layer.
	 * 
	 * @return
	 */
	public List<Integer> getGroundLayer() {
		return Collections.unmodifiableList(groundLayer);
	}

	/**
	 * Returns the upper layers of this map. In this map the ground layer is not
	 * listed anymore. In order to retrieve the non sparse ground layer user
	 * {@link #getGroundLayer()}.
	 * 
	 * @return The sparse upper layer of the map.
	 */
	public List<java.util.Map<Point, Integer>> getSparseLayers() {
		return tileLayer;
	}

	/**
	 * Gets all the names of the tilsets used by this map.
	 * 
	 * @return The names of the tilesets.
	 */
	public List<String> getTilesetNames() {
		return tilesets.stream()
				.map(x -> x.getName())
				.collect(Collectors.toList());
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

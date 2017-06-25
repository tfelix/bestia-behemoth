package net.bestia.model.map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;

/**
 * This is a map data transfer object. It contains all the needed map data to
 * construct maps or map chunks. It can be easily serialized for binary storage
 * inside the database.
 * 
 * It is also used to be directly generated by the map creation algorithms and
 * to be persisted into the database. The {@link net.bestia.model.map.Map} class
 * is optimized for storage of the map parts inside the database. The
 * {@link net.bestia.model.map.Map} for access by the game and {@link MapChunk}
 * for transfer to the client system.
 * 
 * @see {@link net.bestia.model.map.Map} {@link MapChunk}.
 * @author Thomas Felix
 *
 */
public class MapDataDTO implements Serializable {

	private static final long serialVersionUID = 1L;
	private final Rect rect;
	private final int[] groundLayer;
	private final List<Map<Point, Integer>> layers = new ArrayList<>();

	public MapDataDTO(Rect rect) {

		this.rect = Objects.requireNonNull(rect);

		int size = (int) (rect.getHeight() * rect.getWidth());
		groundLayer = new int[size];

	}

	/**
	 * Calculates the index of the x and y coordiante.
	 * 
	 * @param x
	 *            X coordinate.
	 * @param y
	 *            Y coordiante.
	 * @return The index to get the tile out of the internal array.
	 */
	private int getIndex(long x, long y) {
		if (x < 0 || y < 0) {
			throw new IllegalArgumentException("X and Y coordinates can not be negative.");
		}

		// First we normalize the coordiantes into map data space.
		int posX = (int) (x - rect.getOrigin().getX());
		int posY = (int) (y - rect.getOrigin().getY());

		return (int) (posY * rect.getWidth()) + posX;
	}

	/**
	 * Gets the GID from the ground layer.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public int getGroundGid(long x, long y) {
		int index = getIndex(x, y);
		if (index > groundLayer.length) {
			throw new IndexOutOfBoundsException("Given coordiantes are not within the data.");
		}

		return groundLayer[index];
	}

	/**
	 * Puts the given GID to the given x and y coordinates. The coordinates are
	 * GLOBAL map coordinates.
	 * 
	 * @param x
	 *            Global map x coordinate.
	 * @param y
	 *            Global map y coordinate.
	 * @param gid
	 *            The tile ID of the ground tile.
	 */
	public void putGroundLayer(long x, long y, int gid) {
		int index = getIndex(x, y);
		if (index > groundLayer.length) {
			throw new IndexOutOfBoundsException("Given coordiantes are not within the data.");
		}

		groundLayer[index] = gid;
	}

	/**
	 * Returns all layered ids for the given coordiante.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public List<Integer> getLayerGids(long x, long y) {
		final Point p = new Point(x, y);
		final List<Integer> gids = new ArrayList<>();

		for (Map<Point, Integer> layer : layers) {
			Integer lgid = layer.get(p);

			if (lgid != null) {
				gids.add(lgid);
			}
		}

		return gids;
	}

	/**
	 * Sets a tile id onto a given layer. We must start with layer 1 since layer
	 * 0 is the ground layer.
	 * 
	 * @param layer
	 * @param x
	 * @param y
	 * @param gid
	 */
	public void putLayer(int layer, long x, long y, int gid) {
		// Check if the cords are within this chunkg.
		final Point p = new Point(x, y);

		if (!rect.collide(p)) {
			throw new IndexOutOfBoundsException("Given coordiantes are not within the data.");
		}

		if (layers.size() < layer) {
			// Extend list to the given layers.
			for (int i = layer - layers.size(); i > 0; i--) {
				layers.add(new HashMap<>());
			}
		}

		layers.get(layer).put(p, gid);
	}

	/**
	 * Returns the rectangular area represented by this map part data.
	 * 
	 * @return The rectangular area of this part of the map.
	 */
	public Rect getRect() {
		return rect;
	}

	/**
	 * Combines different DTOs into a single one the covered are is extended.
	 * The instances can only be joined if they are adjacent to each other. If
	 * this is not the case an illegal argument exception is thrown.
	 * 
	 * @param rhs
	 * @return
	 */
	public MapDataDTO join(MapDataDTO rhs) {

		// We can only join same dimensions.
		if (rhs.getRect().getWidth() != getRect().getWidth()
				|| rhs.getRect().getHeight() != getRect().getHeight()) {
			throw new IllegalArgumentException("MapDataDTOs have different sizes. Can not join.");
		}

		final long rhsX = rhs.getRect().getX();
		final long rhsX2 = rhsX + rhs.getRect().getWidth();
		final long rhsY = rhs.getRect().getY();
		final long rhsY2 = rhsY + rhs.getRect().getHeight();

		final long x = getRect().getX();
		final long x2 = x + getRect().getWidth();
		final long y = getRect().getY();
		final long y2 = y + getRect().getHeight();

		final long totalWidth = getRect().getWidth() + rhs.getRect().getWidth();
		final long totalHeight = getRect().getHeight() + rhs.getRect().getHeight();

		final Rect joinedRect;

		if (x2 + 1 == rhsX && rhsY == y) {
			// RHS is on right quadrant.
			joinedRect = new Rect(x, y, totalWidth, getRect().getHeight());
		} else if (rhsX2 + 1 == x && rhsY == y) {
			// RHS is on left quadrant.
			joinedRect = new Rect(rhsX, rhsY, totalWidth, getRect().getHeight());
		} else if (rhsX == x && rhsY == y2 + 1) {
			// RHS is on the bottom quadrant.
			joinedRect = new Rect(x, y, getRect().getWidth(), totalHeight);
		} else if (rhsX == x && rhsY2 + 1 == y) {
			// RHS is on the top quadrant.
			joinedRect = new Rect(rhsX, rhsY, getRect().getWidth(), totalHeight);
		} else {
			// Non overlapping.
			throw new IllegalArgumentException("Area is not adjacent to each other.");
		}

		final MapDataDTO joinedData = new MapDataDTO(joinedRect);

		// Since width must be equal for both arrays.
		final int length = (int) rhs.getRect().getWidth();

		// Combine the ground layer data.
		for (int i = 0; i < y2 - y; ++i) {

			int srcPos = i * length;
			int destPos = 2 * i * length;

			System.arraycopy(groundLayer, srcPos, joinedData.groundLayer, destPos, length);

			destPos += length;

			System.arraycopy(rhs.groundLayer, srcPos, joinedData.groundLayer, destPos, length);
		}

		// Copy the upper map layers.
		final int maxLayer = Math.max(layers.size(), rhs.layers.size());

		for (int i = 0; i < maxLayer; ++i) {
			final Map<Point, Integer> layer = new HashMap<>();

			if (layers.size() < i) {
				layer.putAll(layers.get(i));
			}

			if (rhs.layers.size() < i) {
				layer.putAll(rhs.layers.get(i));
			}

			joinedData.layers.add(layer);
		}

		return joinedData;
	}

	/**
	 * Returns a sliced rect from the {@link MapDataDTO}. The given rect must be
	 * fully inside the are covered by this DTO or an
	 * {@link IllegalArgumentException} is thrown.
	 * 
	 * @param rect
	 * @return
	 */
	public MapDataDTO slice(Rect slice) {

		Objects.requireNonNull(rect);
		if (slice.getX() < rect.getX()
				|| slice.getY() > rect.getY()
				|| slice.getWidth() > rect.getWidth()
				|| slice.getHeight() > rect.getHeight()) {
			throw new IllegalArgumentException("Slice is bigger then the area covered by the DTO.");
		}

		final MapDataDTO sliced = new MapDataDTO(slice);

		// This could be possibly done more effectivly. But I am tired for now
		// and this needs to work asap.
		for (long y = slice.getY(); y < slice.getY() + slice.getHeight(); ++y) {
			for (long x = slice.getX(); x < slice.getX() + slice.getWidth(); ++x) {

				int gid = getGroundGid(x, y);
				sliced.putGroundLayer(x, y, gid);

				final List<Integer> layerGids = getLayerGids(x, y);
				int layer = 1;

				for (Integer lgid : layerGids) {
					sliced.putLayer(layer, x, y, lgid);
					layer++;
				}
			}
		}

		return sliced;
	}

	/**
	 * Returns all GIDs which are contained within this {@link MapDataDTO}. This
	 * includes ALL layers of the DTO.
	 * 
	 * @return A set containing all GIDs of the data included in this instance.
	 */
	public Set<Integer> getDistinctGids() {

		final Set<Integer> gids = Arrays.stream(groundLayer).boxed().collect(Collectors.toSet());

		// Now we go over all the over ids and add them to the set.
		final Set<Integer> layerGids = layers.stream()
				.map(Map::values)
				.flatMap(s -> s.stream())
				.collect(Collectors.toSet());

		gids.addAll(layerGids);

		return gids;
	}
}

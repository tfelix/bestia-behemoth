package net.bestia.model.map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;

/**
 * The bestia map consists of multiple small parts cvalled chunks. These parts
 * can be requested by the player client and are delivered to the player via the
 * network. It basically encodes the map data into parts used by the engine.
 * This chunk class is optimized for transfer to the client.
 * 
 * @author Thomas Felix
 *
 */
public class MapChunk implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * How many tiles are contained within one of such chunks in each dimension.
	 */
	public static final int MAP_CHUNK_SIZE = 10;

	/**
	 * The area which is covered by this chunk.
	 */
	public static final int MAP_CHUNK_SIZE_AREA = MAP_CHUNK_SIZE * MAP_CHUNK_SIZE;

	@JsonProperty("gl")
	private final int[] groundLayer;

	@JsonProperty("l")
	private final List<Map<Point, Integer>> layers = new ArrayList<>();

	@JsonProperty("p")
	private final Point position;

	/**
	 * Ctor.
	 * 
	 * @param pos
	 *            Position of this chunk layer.
	 * @param groundLayer
	 *            The ground layer data at this chunk position.
	 */
	public MapChunk(Point pos, int[] groundLayer) {
		this(pos, groundLayer, null);
		// no op.
	}

	public MapChunk(Point pos, List<Integer> groundLayer, List<Map<Point, Integer>> layers) {
		this(pos, groundLayer.stream().mapToInt(i -> i).toArray(), layers);
		// no op.
	}

	public MapChunk(Point pos, int[] groundLayer, List<Map<Point, Integer>> layers) {

		if (groundLayer.length != MAP_CHUNK_SIZE_AREA) {
			throw new IllegalArgumentException(
					"Ground layer is not of the size of the chunk. Must be " + MAP_CHUNK_SIZE_AREA);
		}

		Objects.requireNonNull(pos);
		checkPositiveCords(pos);
		this.position = pos;
		this.groundLayer = new int[MAP_CHUNK_SIZE_AREA];
		System.arraycopy(groundLayer, 0, this.groundLayer, 0, this.groundLayer.length);

		if (layers != null) {
			this.layers.addAll(layers);
		}
	}

	/**
	 * Returns the tile gid from the ground.
	 * 
	 * @param chunkPos
	 * @return
	 */
	public int getGid(Point chunkPos) {
		return getGid(0, chunkPos);
	}

	/**
	 * Gets a tile gid from a specific layer.
	 * 
	 * @param layer
	 * @param chunkPos
	 * @return The tile gid or -1 if the tile is not present.
	 */
	public int getGid(int layer, Point chunkPos) {

		if (layer < 0) {
			return -1;
		}

		if (chunkPos.getX() < 0 || chunkPos.getY() < 0) {
			return -1;
		}

		if (layer == 0) {
			return groundLayer[(int) (chunkPos.getY() * MAP_CHUNK_SIZE + chunkPos.getX())];
		} else {
			// Correct the id since the 0 layer (ground) is saved in an own
			// structure.
			final Map<Point, Integer> layerData = layers.get(layer - 1);
			return layerData.containsKey(chunkPos) ? layerData.get(chunkPos) : -1;
		}
	}

	/**
	 * Calculates the chunk coordinates (these are the coordinates INSIDE the
	 * chunk) from the given world coordinates. The given coordinates must be
	 * positive.
	 * 
	 * @param world
	 *            The world coordinates.
	 * @return The chunk id in which this point is located.
	 */
	public static Point getChunkCords(Point world) {
		checkPositiveCords(world);
		return new Point(world.getX() % MAP_CHUNK_SIZE, world.getY() % MAP_CHUNK_SIZE);
	}

	/**
	 * Calculates the world coordinates of the given chunk coordinates. The
	 * given coordinates must be positive.
	 * 
	 * @param chunk
	 *            The chunk coordinates.
	 * @return The point in world coordinates.
	 */
	public static Point getWorldCords(Point chunk) {
		checkPositiveCords(chunk);
		return new Point(chunk.getX() * MAP_CHUNK_SIZE, chunk.getY() * MAP_CHUNK_SIZE);
	}

	/**
	 * Returns the area in world coordinates which is covered by this chunk. The
	 * coordinates must be 0 or positive.
	 * 
	 * @param chunk
	 *            Chunk coordinates.
	 * @return The area/rect which is covered by this chunk.
	 */
	public static Rect getWorldRect(Point chunk) {
		checkPositiveCords(chunk);
		final Point p = getWorldCords(chunk);
		return new Rect(p.getX(), p.getY(), MAP_CHUNK_SIZE, MAP_CHUNK_SIZE);
	}

	private static void checkPositiveCords(Point cords) {
		if (cords.getX() < 0 || cords.getY() < 0) {
			throw new IllegalArgumentException("Coordinates must be 0 or positive.");
		}
	}

}

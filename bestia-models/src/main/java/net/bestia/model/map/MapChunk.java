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
 * The bestia map consists of multiple small parts. These parts can be requested
 * by the player account and are delivered to the player vie network. It
 * basically encodes the map data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
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

		this.position = Objects.requireNonNull(pos);
		this.groundLayer = new int[MAP_CHUNK_SIZE_AREA];
		System.arraycopy(groundLayer, 0, this.groundLayer, 0, this.groundLayer.length);

		if (layers != null) {
			layers.addAll(Objects.requireNonNull(layers));
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
	 * Gets a tile gid from a higher layer.
	 * 
	 * @param layer
	 * @param chunkPos
	 * @return
	 */
	public int getGid(int layer, Point chunkPos) {

		if (layer < 0) {
			throw new IllegalArgumentException("Layer must be 0 or positive.");
		}

		if (layer == 0) {
			return groundLayer[(int) (chunkPos.getY() * MAP_CHUNK_SIZE + chunkPos.getX())];
		} else {
			return layers.get(layer).get(chunkPos);
		}
	}

	/**
	 * Calculates the chunk coordiantes of the given world coordiantes.
	 * 
	 * @param world
	 *            The world coordiantes.
	 * @return The chunk id in which this point is located.
	 */
	public static Point getChunkCords(Point world) {

		return new Point(world.getX() % MAP_CHUNK_SIZE, world.getY() % MAP_CHUNK_SIZE);
	}

	/**
	 * Calculates the world coordiantes of the given chunk coordiantes.
	 * 
	 * @param chunk
	 *            The chunk coordinates.
	 * @return The point in world coordinates.
	 */
	public static Point getWorldCords(Point chunk) {

		return new Point(chunk.getX() * MAP_CHUNK_SIZE, chunk.getY() * MAP_CHUNK_SIZE);
	}

	/**
	 * Returns the area in world coordiantes which is covered by this chunk.
	 * 
	 * @param chunk
	 *            Chunk coordiantes.
	 * @return The area/rect which is covered by this chunk.
	 */
	public static Rect getWorldRect(Point chunk) {

		final Point p = getWorldCords(chunk);
		return new Rect(p.getX(), p.getY(), MAP_CHUNK_SIZE, MAP_CHUNK_SIZE);
	}

}

package net.bestia.model.map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.shape.Point;

/**
 * The bestia map consists of multiple small parts. These parts can be requested
 * by the player account and are delivered to the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunk implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * How many tiles are contained within one of such chunks.
	 */
	public static int MAP_CHUNK_SIZE = 10;

	@JsonProperty("gl")
	private final int[] groundLayer = new int[MAP_CHUNK_SIZE * MAP_CHUNK_SIZE];

	@JsonProperty("l")
	private final List<Map<Point, Integer>> layers = new ArrayList<>();

	@JsonProperty("p")
	private final Point position;

	public MapChunk(Point pos, int[] groundLayer) {

		this.position = pos;
		System.arraycopy(groundLayer, 0, this.groundLayer, 0, this.groundLayer.length);
	}

	public MapChunk(Point pos, int[] groundLayer, List<Map<Point, Integer>> layers) {

		this.position = pos;
		System.arraycopy(groundLayer, 0, this.groundLayer, 0, this.groundLayer.length);
		layers.addAll(layers);
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
	 * @return
	 */
	public static Point getChunkCords(Point world) {
		return new Point(world.getX() % MAP_CHUNK_SIZE, world.getY() % MAP_CHUNK_SIZE);
	}

}

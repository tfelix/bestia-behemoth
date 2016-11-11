package net.bestia.zoneserver.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bestia.model.map.Tile;
import net.bestia.model.shape.Point;

/**
 * The bestia map consists of multiple small parts. These parts can be requested
 * by the player account and are delivered to the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunk {

	public static int MAP_CHUNK_SIZE = 10;

	private final Tile[] groundLayer = new Tile[MAP_CHUNK_SIZE * MAP_CHUNK_SIZE];
	private final List<Map<Point, Tile>> topLayers = new ArrayList<>();
	
	public Tile getTile(Point chunkPos) {
		return groundLayer[(int)(chunkPos.getY() * MAP_CHUNK_SIZE + chunkPos.getX())];
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

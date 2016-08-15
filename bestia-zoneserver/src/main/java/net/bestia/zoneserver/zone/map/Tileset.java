package net.bestia.zoneserver.zone.map;

import java.util.HashMap;

import net.bestia.model.zone.Size;

public class Tileset {

	private static final Size TILE_SIZE = new Size(32, 32);

	private String name;
	private int firstGID;
	private Size tilesetSize;
	private final int tileCount;

	private java.util.Map<Integer, TileProperties> tileProperties = new HashMap<>();

	public Tileset() {
		final int tileCountX = tilesetSize.getWidth() / TILE_SIZE.getWidth();
		final int tileCountY = tilesetSize.getHeight() / TILE_SIZE.getHeight();
		tileCount = tileCountX * tileCountY;
	}

	public int getFirstGID() {
		return firstGID;
	}

	public TileProperties getProperties(int gid) {
		return tileProperties.get(gid);
	}

	/**
	 * Checks if this tile is contained withing this tileset. This is done via
	 * gid compairson.
	 * 
	 * @param tile
	 *            The tile to check if it is contained within this tileset.
	 * @return TRUE if this tile blongs to this tileset. FALSE otherwise.
	 */
	public boolean contains(Tile tile) {
		return (firstGID + tileCount) <= tile.getGid() && tile.getGid() >= firstGID;
	}

	/**
	 * Returns the name of this tileset file.
	 * 
	 * @return The name of this tileset file.
	 */
	public String getName() {
		return name;
	}
}

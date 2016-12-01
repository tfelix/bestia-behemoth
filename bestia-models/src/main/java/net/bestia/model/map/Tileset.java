package net.bestia.model.map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;

import net.bestia.model.domain.Tile;
import net.bestia.model.geometry.Size;

/**
 * Data of a tileset which is used by the bestia map creation. It holds all
 * needed data for each tile in this tileset. The information can be queried.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Tileset implements Serializable {

	private static final long serialVersionUID = 1L;
	public static final Size TILE_SIZE = new Size(32, 32);

	private final String name;
	private final int firstGID;
	private final Size tilesetSize;
	private final long tileCount;

	private final java.util.Map<Integer, TileProperties> tileProperties = new HashMap<>();

	public Tileset(String name, Size size, int firstGID) {

		this.name = Objects.requireNonNull(name);
		this.tilesetSize = Objects.requireNonNull(size);
		this.firstGID = firstGID;

		final long tileCountX = tilesetSize.getWidth() / TILE_SIZE.getWidth();
		final long tileCountY = tilesetSize.getHeight() / TILE_SIZE.getHeight();
		tileCount = tileCountX * tileCountY;
	}

	/**
	 * Returns the first gid of the tiles in this set. The GID are used to
	 * identify the tiles it is a globally usable id which is connected to a
	 * single tileset.
	 * 
	 * @return The first used GID of the tiles in this tileset.
	 */
	public int getFirstGID() {
		return firstGID;
	}

	/**
	 * Sets the properties of a given tile with a guid.
	 * 
	 * @param gid
	 *            The gid of the tile.
	 * @param props
	 *            The properties of the tile.
	 */
	public void setProperties(int gid, TileProperties props) {
		if (!contains(gid)) {
			throw new IllegalArgumentException("Gid is not part of this tileset.");
		}
		tileProperties.put(gid, Objects.requireNonNull(props));
	}

	/**
	 * Returns the {@link TileProperties} of the tile with the given gid. If the
	 * tile has no properties then null is returned. If the tile is not
	 * contained within this tileset {@link IllegalArgumentException} is thrown.
	 * 
	 * @param gid
	 *            The GID of the tile.
	 * @return The {@link TileProperties} of the tile or NULL if the tile had no
	 *         properties.
	 * @throws IllegalArgumentException
	 *             if the gid is not part of this tileset.
	 */
	public TileProperties getProperties(int gid) {
		if (!contains(gid)) {
			throw new IllegalArgumentException("GID is not contained withing this tileset.");
		}
		return tileProperties.get(gid);
	}

	/**
	 * Checks if this tile is contained within this tileset. This is done via
	 * gid comparison.
	 * 
	 * @param tile
	 *            The tile to check if it is contained within this tileset.
	 * @return TRUE if this tile belongs to this tileset. FALSE otherwise.
	 */
	public boolean contains(Tile tile) {
		Objects.requireNonNull(tile);
		return contains(tile.getGid());
	}

	/**
	 * Checks if this tile is contained within this tileset. This is done via
	 * gid comparison.
	 * 
	 * @param tile
	 *            The tile to check if it is contained within this tileset.
	 * @return TRUE if this tile belongs to this tileset. FALSE otherwise.
	 */
	public boolean contains(int gid) {
		return (firstGID + tileCount) >= gid && gid >= firstGID;
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

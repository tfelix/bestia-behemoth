package net.bestia.model.map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.geometry.Size;

/**
 * Data of a tileset which is used by the bestia map creation. It holds all
 * needed data for each tile in this tileset. The information can be queried.
 * 
 * @author Thomas Felix
 *
 */
public class Tileset implements Serializable {

	/**
	 * This class is used for simple tileset representations send to the client.
	 * The client does not need full information about all tiles thus we can
	 * leave it out. But we need this information to be serializable for the
	 * server.
	 *
	 */
	public static class SimpleTileset {

		@JsonProperty("mingid")
		private final int minGID;

		@JsonProperty("maxgid")
		private final int maxGid;

		@JsonProperty("name")
		private final String name;

		public SimpleTileset(Tileset tileset) {

			this.minGID = tileset.minGID;
			this.maxGid = tileset.maxGid;
			this.name = tileset.name;
		}

	}

	private static final long serialVersionUID = 1L;

	@JsonIgnore
	public static final Size TILE_SIZE = new Size(32, 32);

	private final String name;

	@JsonProperty("mingid")
	private final int minGID;

	@JsonProperty("maxgid")
	private final int maxGid;

	@JsonProperty("size")
	private final Size size;

	@JsonProperty("properties")
	private final java.util.Map<Integer, TileProperties> tileProperties = new HashMap<>();

	@JsonIgnore
	private int tileCount;

	/**
	 * 
	 * @param name
	 * @param size
	 *            Size in tiles.
	 * @param firstGID
	 */
	public Tileset(String name, Size size, int firstGID) {

		this.name = Objects.requireNonNull(name);
		this.size = Objects.requireNonNull(size);

		this.minGID = firstGID;
		this.maxGid = firstGID + (int) (size.getHeight() * size.getWidth());
		this.tileCount = this.maxGid - this.minGID;
	}

	/**
	 * Returns the first gid of the tiles in this set. The GID are used to
	 * identify the tiles it is a globally usable id which is connected to a
	 * single tileset.
	 * 
	 * @return The first used GID of the tiles in this tileset.
	 */
	public int getStartGID() {
		return minGID;
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
	public boolean contains(int gid) {
		return (minGID + tileCount) >= gid && gid >= minGID;
	}

	/**
	 * Returns the name of this tileset file.
	 * 
	 * @return The name of this tileset file.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the simple representation of the tileset for use to be send to
	 * the client. This version does not contain any tile property information.
	 * 
	 * @return A simplified version of Tileset.
	 */
	@JsonIgnore
	public SimpleTileset getSimpleTileset() {
		return new SimpleTileset(this);
	}
}

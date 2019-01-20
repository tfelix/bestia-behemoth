package net.bestia.model.map

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonInclude.Include
import net.bestia.model.geometry.Size

import java.io.Serializable
import java.util.HashMap
import java.util.Objects

/**
 * Data of a tileset which is used by the bestia map creation. It holds all
 * needed data for each tile in this tileset. The information can be queried.
 *
 * @author Thomas Felix
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Tileset

@JsonCreator
constructor(
    @JsonProperty("name") name: String,
    @JsonProperty("size") size: Size,
    @param:JsonProperty("mingid") @field:JsonProperty("mingid")
    /**
     * Returns the first gid of the tiles in this set. The GID are used to
     * identify the tiles it is a globally usable id which is connected to a
     * single tileset.
     *
     * @return The first used GID of the tiles in this tileset.
     */
    val startGID: Int) : Serializable {

  /**
   * Returns the name of this tileset file.
   *
   * @return The name of this tileset file.
   */
  val name: String

  @JsonProperty("maxgid")
  private val maxGid: Int

  @JsonProperty("size")
  private val size: Size

  @JsonProperty("props")
  @JsonInclude(Include.NON_NULL)
  private val tileProperties = HashMap<Int, TileProperties>()

  @JsonIgnore
  private val tileCount: Int

  /**
   * Returns the simple representation of the tileset for use to be send to
   * the client. This version does not contain any tile property information.
   *
   * @return A simplified version of Tileset.
   */
  val simpleTileset: SimpleTileset
    @JsonIgnore
    get() = SimpleTileset(this)

  /**
   * This class is used for simple tileset representations send to the client.
   * The client does not need full information about all tiles thus we can
   * leave it out. But we need this information to be serializable for the
   * server.
   *
   */
  class SimpleTileset(tileset: Tileset) : Serializable {

    @JsonProperty("mingid")
    private val minGID: Int

    @JsonProperty("maxgid")
    private val maxGid: Int

    @JsonProperty("name")
    private val name: String

    init {

      this.minGID = tileset.startGID
      this.maxGid = tileset.maxGid
      this.name = tileset.name
    }

    override fun toString(): String {
      return String.format("TS[name: %s]", name)
    }

    companion object {

      private const val serialVersionUID = 1L
    }
  }

  init {

    this.name = Objects.requireNonNull(name)
    this.size = Objects.requireNonNull(size)
    this.maxGid = startGID + (size.height * size.width).toInt()
    this.tileCount = this.maxGid - this.startGID
  }/*
		if(props != null) {
			this.tileProperties.putAll(props);
		}*/

  /**
   * Sets the properties of a given tile with a guid.
   *
   * @param gid
   * The gid of the tile.
   * @param props
   * The properties of the tile.
   */
  fun setProperties(gid: Int, props: TileProperties) {
    if (!contains(gid)) {
      throw IllegalArgumentException("Gid is not part of this tileset.")
    }
    tileProperties[gid] = Objects.requireNonNull(props)
  }

  /**
   * Returns the [TileProperties] of the tile with the given gid. If the
   * tile has no properties then null is returned. If the tile is not
   * contained within this tileset [IllegalArgumentException] is thrown.
   *
   * @param gid
   * The GID of the tile.
   * @return The [TileProperties] of the tile or NULL if the tile had no
   * properties.
   * @throws IllegalArgumentException
   * if the gid is not part of this tileset.
   */
  fun getProperties(gid: Int): TileProperties {
    if (!contains(gid)) {
      throw IllegalArgumentException("GID is not contained withing this tileset.")
    }
    return tileProperties[gid]
  }

  /**
   * Checks if this tile is contained within this tileset. This is done via
   * gid comparison.
   *
   * @param gid
   * The tile to check if it is contained within this tileset.
   * @return TRUE if this tile belongs to this tileset. FALSE otherwise.
   */
  operator fun contains(gid: Int): Boolean {
    return startGID + tileCount >= gid && gid >= startGID
  }

  companion object {

    private const val serialVersionUID = 1L

    @JsonIgnore
    val TILE_SIZE = Size(32, 32)
  }
}

package net.bestia.model.map

import java.io.Serializable

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

/**
 * The [MapData] is raw map file data which is used by the map service in
 * order to query and generate the player map data. It lies in the form a binary
 * compressed data to support the huge bestia maps.
 *
 * Certain indices are set and used for fast query of the map parts.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "map_data", indexes = [
  Index(columnList = "x", name = "x_idx"),
  Index(columnList = "y", name = "y_idx")
])
@IdClass(MapData.MapDataPK::class)
class MapData(
    @Id
    val x: Long = 0,
    @Id
    val y: Long = 0,
    @Id
    val width: Long = 0,
    @Id
    val height: Long = 0,
    /**
     * This data storages contain [MapDataDTO]s which are encoded map
     * data.
     */
    @Lob
    @Column(nullable = false, length = 50000)
    val data: ByteArray
) : Serializable {

  /**
   * Composite primary key helper class.
   *
   */
  internal data class MapDataPK(
      val x: Long,
      val y: Long,
      val width: Long,
      val height: Long
  ) : Serializable {
    // Needed for Hibernate
    private constructor() : this(0, 0, 0, 0)
  }

  override fun toString(): String {
    return "MapData[x: $x, y: $y, w: $width, h: $height, data: ${data.size} bytes]"
  }
}

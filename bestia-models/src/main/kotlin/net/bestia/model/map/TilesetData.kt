package net.bestia.model.map

import net.bestia.model.AbstractEntity
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * The [TilesetData] holds information about the concrete pack to load. It
 * can be queried for the GID in order to get the appropriate tileset
 * information.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "tileset", indexes = [
  Index(columnList = "min_gid"),
  Index(columnList = "max_gid")
])
class TilesetData(
    @Column(name = "min_gid")
    var minGid: Long,

    @Column(name = "max_gid")
    var maxGid: Long,

    /**
     * JSON serialized description of the tileset.
     */
    val data: String
) : AbstractEntity(), Serializable {

  override fun toString(): String {
    return "Tileset[id: $id, minGid: $minGid, maxGid: $maxGid]"
  }
}

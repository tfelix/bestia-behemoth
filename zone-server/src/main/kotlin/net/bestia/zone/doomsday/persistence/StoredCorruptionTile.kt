package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.Length
import java.nio.ByteBuffer

/** One 4 km tile of the Withering's corruption, laid out like a mana tile. The version stamps drop another world's. */
@Entity
@Table(name = "corruption_tile")
class StoredCorruptionTile(
  @Id
  @Column(name = "tile_key", nullable = false)
  var tileKey: Long = 0,

  /** The cells as big-endian doubles, row by row. */
  @Lob
  @Column(nullable = false, length = Length.LONG32)
  var intensity: ByteArray = ByteArray(0),

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  fun cells(): DoubleArray {
    val buffer = ByteBuffer.wrap(intensity).asDoubleBuffer()
    return DoubleArray(buffer.remaining()).also { buffer.get(it) }
  }

  companion object {
    fun bytesOf(cells: DoubleArray): ByteArray {
      val buffer = ByteBuffer.allocate(cells.size * Double.SIZE_BYTES)
      buffer.asDoubleBuffer().put(cells)
      return buffer.array()
    }
  }
}

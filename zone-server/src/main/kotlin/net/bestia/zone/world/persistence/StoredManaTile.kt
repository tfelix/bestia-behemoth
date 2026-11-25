package net.bestia.zone.world.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.Length
import java.nio.ByteBuffer

/**
 * One 4 km tile of the mana field: the change over the start density on each of its cells. The version stamps
 * drop a tile of another world, as `ScorchMark` does.
 */
@Entity
@Table(name = "mana_tile")
class StoredManaTile(
  /** `(tileX shl 32) or tileY`, see `ManaField.tileKey`. */
  @Id
  @Column(name = "tile_key", nullable = false)
  var tileKey: Long = 0,

  /** The cells as big-endian doubles, row by row. */
  @Lob
  @Column(nullable = false, length = Length.LONG32)
  var delta: ByteArray = ByteArray(0),

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  fun cells(): DoubleArray {
    val buffer = ByteBuffer.wrap(delta).asDoubleBuffer()
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

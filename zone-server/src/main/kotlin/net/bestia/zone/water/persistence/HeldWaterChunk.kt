package net.bestia.zone.water.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.bestia.worldgen.core.ChunkPos
import java.io.Serializable

/**
 * A chunk the water simulation held when last saved. Its water is already in the chunk's edits; this row only
 * says it may still be moving, so a restart carries the flood on instead of freezing it mid-flow.
 */
@Entity
@Table(name = "water_held_chunk")
class HeldWaterChunk(
  @EmbeddedId
  var id: Key = Key(),

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  val chunk: ChunkPos
    get() = ChunkPos(id.chunkX, id.chunkY, id.chunkZ)

  @Embeddable
  data class Key(
    @Column(name = "chunk_x", nullable = false)
    var chunkX: Int = 0,

    @Column(name = "chunk_y", nullable = false)
    var chunkY: Int = 0,

    @Column(name = "chunk_z", nullable = false)
    var chunkZ: Int = 0,
  ) : Serializable {

    companion object {
      fun of(chunk: ChunkPos): Key {
        return Key(chunk.x, chunk.y, chunk.z)
      }
    }
  }
}

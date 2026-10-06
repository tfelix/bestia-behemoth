package net.bestia.zone.world.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Lob
import jakarta.persistence.Table
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.store.ChunkEdit
import net.bestia.worldgen.store.DeflatedBlobStore
import org.hibernate.Length
import java.io.Serializable
import java.time.Instant
import net.bestia.zone.world.stream.ChunkPatchCodec
import net.bestia.zone.world.stream.ChunkService

/**
 * One edited chunk, kept the way [net.bestia.worldgen.store.ChunkStore] holds it: removals over the generated
 * base, or the whole chunk once it was baked.
 *
 * Both version stamps, for `GroundLayerMark`'s reason: removals only mean something on the base they were taken
 * from, and `pipelineVersion` does not fold the seed.
 */
@Entity
@Table(name = "chunk_edit")
class PersistedChunkEdit(
  @EmbeddedId
  var id: Key = Key(),

  @Column(nullable = false)
  var revision: Int = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  var kind: Kind = Kind.DELTA,

  // A bare @Lob is a 255-byte tinyblob on MariaDB.
  @Lob
  @Column(nullable = false, length = Length.LONG32)
  var payload: ByteArray = ByteArray(0),

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  /** Wall clock, for a human reading the table. */
  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now()

  enum class Kind {
    /** [payload] is the removals, as [ChunkPatchCodec] writes them. */
    DELTA,

    /** [payload] is the run-length encoded chunk, framed by [DeflatedBlobStore]. */
    BAKED
  }

  @Embeddable
  data class Key(
    @Column(name = "chunk_x", nullable = false)
    var chunkX: Int = 0,

    @Column(name = "chunk_y", nullable = false)
    var chunkY: Int = 0,

    @Column(name = "chunk_z", nullable = false)
    var chunkZ: Int = 0,
  ) : Serializable

  fun toSavedEdit(): ChunkService.SavedEdit {
    val chunk = ChunkPos(id.chunkX, id.chunkY, id.chunkZ)
    val edit = when (kind) {
      Kind.DELTA -> ChunkEdit.Delta(ChunkPatchCodec.decode(payload))
      Kind.BAKED -> ChunkEdit.Baked(DeflatedBlobStore.unframe(chunk, payload))
    }

    return ChunkService.SavedEdit(chunk, revision, edit)
  }

  companion object {
    fun of(saved: ChunkService.SavedEdit, worldShapeVersion: Long, pipelineVersion: Long): PersistedChunkEdit {
      val (kind, payload) = when (val edit = saved.edit) {
        is ChunkEdit.Delta -> Kind.DELTA to ChunkPatchCodec.encode(edit.removals)
        is ChunkEdit.Baked -> Kind.BAKED to DeflatedBlobStore.frame(edit.rle)
      }
      val key = Key(saved.chunk.x, saved.chunk.y, saved.chunk.z)

      return PersistedChunkEdit(key, saved.revision, kind, payload, worldShapeVersion, pipelineVersion)
    }
  }
}

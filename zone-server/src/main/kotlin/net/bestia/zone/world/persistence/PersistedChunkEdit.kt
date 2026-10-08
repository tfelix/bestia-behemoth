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
import net.bestia.zone.world.stream.ChunkEditCodec
import net.bestia.zone.world.stream.ChunkService

/**
 * One edited chunk, kept the way [net.bestia.worldgen.store.ChunkStore] holds it: edits over the generated
 * base, or the whole chunk once it was baked.
 *
 * Both version stamps, for `GroundLayerMark`'s reason: edits only mean something on the base they were made
 * on, and `pipelineVersion` does not fold the seed.
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

  /**
   * Which [ChunkEditCodec] wrote a [Kind.DELTA] payload. A [Kind.BAKED] blob carries its own version, so it is 0
   * there, and so is every row written before this column existed.
   */
  @Column(name = "delta_format", nullable = false)
  var deltaFormat: Int = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  /** Wall clock, for a human reading the table. */
  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now()

  enum class Kind {
    /** [payload] is the edits, as [ChunkEditCodec] writes them. */
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

  /** A delta from before edits named their block: it cannot be read as edits, and nothing converts it. */
  val isLegacyDelta: Boolean
    get() = kind == Kind.DELTA && deltaFormat != ChunkEditCodec.FORMAT

  fun toSavedEdit(): ChunkService.SavedEdit {
    val chunk = ChunkPos(id.chunkX, id.chunkY, id.chunkZ)
    val edit = when (kind) {
      Kind.DELTA -> ChunkEdit.Delta(ChunkEditCodec.decode(payload))
      Kind.BAKED -> ChunkEdit.Baked(DeflatedBlobStore.unframe(chunk, payload))
    }

    return ChunkService.SavedEdit(chunk, revision, edit)
  }

  companion object {
    fun of(saved: ChunkService.SavedEdit, worldShapeVersion: Long, pipelineVersion: Long): PersistedChunkEdit {
      val key = Key(saved.chunk.x, saved.chunk.y, saved.chunk.z)

      return when (val edit = saved.edit) {
        is ChunkEdit.Delta -> PersistedChunkEdit(
          key, saved.revision, Kind.DELTA, ChunkEditCodec.encode(edit.edits), ChunkEditCodec.FORMAT,
          worldShapeVersion, pipelineVersion
        )

        is ChunkEdit.Baked -> PersistedChunkEdit(
          key, saved.revision, Kind.BAKED, DeflatedBlobStore.frame(edit.rle), 0, worldShapeVersion, pipelineVersion
        )
      }
    }
  }
}

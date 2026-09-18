package net.bestia.zone.world.ground

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * One ground layer's cells for one chunk column.
 *
 * ### One row per column *and layer*
 *
 * `ScorchMark` keys on the column alone because scorch is the only thing it stores. A column can be worn and
 * bloodied and neither, so the layer is part of the key: a column somebody walked across but nothing died on
 * costs one row, and a layer that fades away is deleted without disturbing the others.
 *
 * ### Written far more often than a scar, and therefore not written the same way
 *
 * `ScorchRegistry` persists on every `burn()`, which is once per fire. Wear is touched on every footfall, so
 * the same rule would be a database write per step per creature. `GroundWearRegistry` coalesces instead: the
 * row is written when the column is evicted or when the flush interval comes round, and a crash costs a few
 * minutes of footfalls rather than a path.
 *
 * ### `lastDecayedSecond` is a Bestia second, not an `Instant`
 *
 * That is the axis the clock and the weather are indexed on, the same choice `ScorchMark.burnedAtSecond`
 * makes. [occurredAt] is kept beside it for whoever reads the table by hand and is never read by code.
 *
 * ### Two version stamps, for `WorldObjectDivergence`'s reasons
 *
 * [worldShapeVersion] catches a reseeded or resized world, [pipelineVersion] a rebuilt one. Both, because
 * `pipelineVersion` folds stage and params versions but **not the seed**. A row for ground that no longer
 * exists is worse than absent: these cells are *drawn* on whatever terrain now occupies those coordinates.
 */
@Entity
@Table(name = "ground_layer")
class GroundLayerMark(
  @EmbeddedId
  var id: Key = Key(),

  /** See [ColumnLevels]: one byte per cell, `localY * size + localX`. */
  @Lob
  @Column(nullable = false)
  var cells: ByteArray = ByteArray(0),

  @Column(name = "last_decayed_second", nullable = false)
  var lastDecayedSecond: Long = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  /** Wall clock, for a human reading the table. Never read by code - see the class note. */
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant = Instant.now()

  /**
   * @property columnKey the packing [ColumnKey] owns, so nothing here shifts chunk coordinates by hand
   * @property layerId `GroundLayer.wireId`, not its ordinal - an enum reordered in source must not silently
   *   turn every stored path into a bloodstain
   */
  @Embeddable
  data class Key(
    @Column(name = "column_key", nullable = false)
    var columnKey: Long = 0,

    @Column(name = "layer_id", nullable = false)
    var layerId: Int = 0,
  ) : Serializable
}

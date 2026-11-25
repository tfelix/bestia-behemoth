package net.bestia.zone.prop.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.entity.StaticEntityKind
import java.time.Instant

/**
 * A scar thick mana left on the land: one per mana field cell at most, so the cell names it. The version stamps
 * drop scars of another world, as `ScorchMark` does.
 */
@Entity
@Table(name = "omen_scar")
class OmenScar(
  /** The mana field cell, `(cellX shl 32) or cellY`. */
  @Id
  @Column(name = "cell_key", nullable = false)
  var cellKey: Long = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  var kind: StaticEntityKind = StaticEntityKind.OMEN_STONE_RING,

  @Column(nullable = false)
  var x: Long = 0,

  @Column(nullable = false)
  var y: Long = 0,

  @Column(nullable = false)
  var z: Long = 0,

  @Column(nullable = false)
  var yaw: Float = 0f,

  @Column(nullable = false)
  var variant: Int = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {
  /** Wall clock, for a human reading the table. */
  @Column(name = "raised_at", nullable = false)
  var raisedAt: Instant = Instant.now()
}

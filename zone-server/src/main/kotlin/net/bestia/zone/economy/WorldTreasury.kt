package net.bestia.zone.economy

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * The coin the world has not handed out: gold still in the ground, plus everything spent back into it.
 *
 * One row, unlike [SettlementLedger]. A settlement at its reference needs no row because its deviation is
 * zero; the reserve has no reference to be at, and a missing row would read as a world with no gold left
 * rather than one nobody has touched.
 *
 * Version stamped for [SettlementLedger]'s reason and one of its own: the reserve is only meaningful
 * against the settlement treasuries it was counted with, and a reseeded world has different ones.
 */
@Entity
@Table(name = "world_treasury")
class WorldTreasury(
  @Id
  @Column(name = "id", nullable = false)
  var id: Int = SINGLETON,

  @Column(nullable = false)
  var reserve: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {
  /** Wall clock, for a human reading the table. Never read by code. */
  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now()

  companion object {
    const val SINGLETON = 1
  }
}

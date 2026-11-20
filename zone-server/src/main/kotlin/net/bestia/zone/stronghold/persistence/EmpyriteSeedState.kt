package net.bestia.zone.stronghold.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A seed of the pool that has had its turn to wake. Where it lies is the world generator's answer; this row only
 * says whether it woke or was skipped. A seed with no row still sleeps.
 *
 * The two versions guard against a row outliving the world it was written for, as `DugTreasureCache`'s do.
 */
@Entity
@Table(name = "empyrite_seed")
class EmpyriteSeedState(
  /** The seed's rank in the pool, which is its id within one world. */
  @Id
  @Column(name = "seed_rank", nullable = false)
  var rank: Int = 0,

  /** False for a seed that was skipped. It never wakes. */
  @Column(nullable = false)
  var awake: Boolean = false,

  @Column(name = "decided_at", nullable = false)
  var decidedAt: Instant = Instant.EPOCH,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
)

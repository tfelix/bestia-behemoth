package net.bestia.zone.treasure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A treasure cache somebody has dug at. The cache itself is the world generator's; this row is only how far the
 * trench above it got, and whether it was opened. An opened cache is gone for good.
 *
 * The two versions guard against a row outliving the world it was dug in, as `HeldWaterChunk`'s do.
 */
@Entity
@Table(name = "treasure_cache_dug")
class DugTreasureCache(
  @Id
  @Column(name = "cache_id", nullable = false)
  var cacheId: Long = 0,

  @Column(name = "dug_metres", nullable = false)
  var dugMetres: Double = 0.0,

  @Column(nullable = false)
  var opened: Boolean = false,

  /** Which way the trench climbs out, as a unit vector. The first digger's side; whoever resumes keeps it. */
  @Column(name = "toward_x", nullable = false)
  var towardX: Double = 1.0,

  @Column(name = "toward_y", nullable = false)
  var towardY: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
)

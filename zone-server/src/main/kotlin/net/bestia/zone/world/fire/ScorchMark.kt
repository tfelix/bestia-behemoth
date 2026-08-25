package net.bestia.zone.world.fire

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/**
 * Which square metres of one chunk column are burnt, and when the fire that burnt them started.
 *
 * ### One row per chunk, not one per cell
 *
 * A 20 m fire burns on the order of 1250 cells across four chunks. As rows that is 1250 inserts and something
 * like a hundred kilobytes with the index; as masks it is four upserts and about 680 bytes, and **an extra
 * burnt cell inside a chunk already scarred costs nothing at all**. The only thing per-cell rows buy is a
 * per-cell timestamp, and [burnedAtSecond] below is deliberately not one.
 *
 * ### `burnedAtSecond` is the fire's ignition time, not the cell's burn-out time
 *
 * Every column a fire touches is stamped with the same instant, and that is load bearing rather than a
 * simplification. Regrowth integrates rain from this instant, so columns carrying different timestamps
 * accumulate over different windows and erode at different rates - which would draw a **straight seam along a
 * 32 m chunk boundary** through the middle of a healing scar. Sharing the fire's own instant makes a
 * multi-chunk scar heal as one scar.
 *
 * It is a **Bestia** second rather than a wall-clock instant, because that is the axis the weather field is
 * indexed on: regrowth asks `WeatherService` what fell between then and now, and `WeatherModel` is
 * `f(seed, region, dayOfWorld)`. Storing an `Instant` would mean converting on every pass through a
 * speed factor that is itself configuration. [occurredAt] is kept beside it for whoever reads the table by
 * hand, and is never read by code - the same split `WorldObjectDivergence` draws.
 *
 * Re-burning a column later takes the newer instant and resets that column's clock, so two fires over the same
 * ground at different times *can* still leave a boundary - but it follows the second fire's own edge rather
 * than the chunk grid, which is what actually happened.
 *
 * ### Two version stamps, for `WorldObjectDivergence`'s reasons
 *
 * [worldShapeVersion] catches a reseeded or resized world, [pipelineVersion] a rebuilt one. Both, because
 * `pipelineVersion` folds stage and params versions but **not the seed** - the hole that cost
 * `WorldObjectDivergence` a guard it looked like it had. A row for ground that no longer exists is worse than
 * absent: this mask is drawn on whatever terrain now occupies those coordinates.
 */
@Entity
@Table(name = "ground_scorch")
class ScorchMark(
  /**
   * `(chunkX shl 32) or (chunkY and 0xFFFFFFFF)` - the packing `WorldObjectResidencyService` already uses for
   * a chunk column, so the two agree about what names a column without a shared helper to forget to call.
   */
  @Id
  @Column(name = "column_key", nullable = false)
  var columnKey: Long = 0,

  /** See [ColumnMask]: `chunkSize²` bits, `localY * size + localX`. */
  @Lob
  @Column(nullable = false)
  var mask: ByteArray = ByteArray(0),

  @Column(name = "burned_at_second", nullable = false)
  var burnedAtSecond: Long = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {
  /** Wall clock, for a human reading the table. Never read by code - see [burnedAtSecond]. */
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant = Instant.now()
}

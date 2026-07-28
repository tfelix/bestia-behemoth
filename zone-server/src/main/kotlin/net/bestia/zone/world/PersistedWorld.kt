package net.bestia.zone.world

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * One world's identity and the settings it was created with.
 *
 * Small but load bearing, because it is the *only* authoritative record of the terrain. Rasters, vector
 * features and every chunk are pure functions of the seed and these dimensions, so they are regenerated rather
 * than stored - which means this row is what makes the world the same world after a restart. Lose it and the
 * seed is gone; change a field in it and every chunk in the world moves.
 *
 * The three version numbers are recorded for the same reason. Once a world has player edits in it its pipeline
 * version is frozen: the edits are a delta over a generated base, so a pipeline change shifts the ground out
 * from under them. Storing the version the world was born with is what lets the server notice that on the next
 * boot instead of silently corrupting the deltas. See [WorldService].
 */
@Entity
@Table(
  name = "world",
  indexes = [
    Index(name = "idx_world_name", columnList = "name", unique = true),
  ]
)
class PersistedWorld(

  /** Display name, unique. The first world of any server is called Genesis. */
  @Column(unique = true, nullable = false, updatable = false)
  val name: String,

  /** Everything about the terrain derives from this. */
  @Column(nullable = false, updatable = false)
  val seed: Long,

  @Column(nullable = false, updatable = false)
  val widthCells: Int,

  @Column(nullable = false, updatable = false)
  val heightCells: Int,

  /** Edge length of one world cell in metres. A kilometre by default, so cells are kilometres. */
  @Column(nullable = false, updatable = false)
  val cellSizeMetres: Double,

  @Column(nullable = false, updatable = false)
  val chunkSize: Int,

  @Column(nullable = false, updatable = false)
  val chunkHeight: Int,

  @Column(nullable = false, updatable = false)
  val voxelSizeMetres: Double,

  @Column(nullable = false, updatable = false)
  val seaLevelMetres: Double,

  /**
   * Whether the world's east/west and north/south edges are the same place.
   *
   * Stored rather than read from configuration for the same reason the dimensions are: the ocean margin that
   * hides the seam is baked into the terrain, so a world generated unwrapped stays unwrapped whatever the
   * config file says afterwards.
   */
  @Column(nullable = false, updatable = false)
  val wrapX: Boolean,

  @Column(nullable = false, updatable = false)
  val wrapY: Boolean,

  /**
   * Version vector of the pipeline that generated this world, folded over every stage and its upstreams.
   *
   * Not `updatable`: if the running build disagrees with it, that is a fact to report rather than a field to
   * quietly bring up to date.
   */
  @Column(nullable = false, updatable = false)
  val pipelineVersion: Long,

  /** Hash of the block id assignment. Same terrain, different rock, if this moves. */
  @Column(nullable = false, updatable = false)
  val blockPaletteVersion: Long,

  /** Chunk encoding version. A chunk written under a different one cannot even be decoded. */
  @Column(nullable = false, updatable = false)
  val chunkFormatVersion: Int,

  /**
   * [net.bestia.worldgen.core.WorldConfig.shapeVersion] of the config this world was generated from.
   *
   * The three versions above say whether this build's *generator* still matches. This says whether this
   * *row* still does - whether the columns here can rebuild the config the world was born with. They can
   * only disagree one way: a `WorldConfig` field that decides terrain and has no column here, which comes
   * back as its default and moves the coastline. See [WorldService].
   */
  @Column(nullable = false, updatable = false)
  val shapeVersion: Long,

  @Column(nullable = false, updatable = false)
  val createdAt: Instant
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** Width of the world in metres. */
  val widthMetres: Double get() = widthCells * cellSizeMetres

  val heightMetres: Double get() = heightCells * cellSizeMetres

  override fun toString() =
    "PersistedWorld[$name, seed $seed, ${widthCells}x$heightCells cells, created $createdAt]"

  companion object {
    /** The first world of any server. */
    const val GENESIS = "Genesis"
  }
}

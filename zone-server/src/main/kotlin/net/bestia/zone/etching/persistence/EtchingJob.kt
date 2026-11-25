package net.bestia.zone.etching.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Runic Etching under way on a Bestia locked into a Carving Table. It runs on its own until [endsAt], also with the
 * Artificer away or offline, so everything the roll needs is taken when it starts.
 */
@Entity
@Table(name = "etching_job")
class EtchingJob(
  /** One etching per Bestia at a time. */
  @Id
  @Column(name = "player_bestia_id", nullable = false)
  var playerBestiaId: Long = 0,

  @Column(name = "structure_id", nullable = false)
  var structureId: Long = 0,

  @Column(name = "artificer_master_id", nullable = false)
  var artificerMasterId: Long = 0,

  @Column(name = "artificer_name", nullable = false)
  var artificerName: String = "",

  /** The cut gem the Artificer paid in; it goes back to them when the etching is called off. */
  @Column(name = "gem_item_id", nullable = false)
  var gemItemId: Long = 0,

  @Column(name = "recipe_id", nullable = false)
  var recipeId: Long = 0,

  @Column(name = "chance", nullable = false)
  var chance: Float = 0f,

  @Column(name = "ends_at", nullable = false)
  var endsAt: Instant = Instant.EPOCH,
)

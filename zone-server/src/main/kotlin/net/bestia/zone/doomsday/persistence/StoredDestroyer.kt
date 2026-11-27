package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** The Destroyer of the current world: where it walks, how strong it grew and when it rises again. One row at most. */
@Entity
@Table(name = "destroyer")
class StoredDestroyer(
  @Id
  var id: Long = ONLY,

  /** The species of the sealed boss it was. */
  @Column(name = "bestia_id", nullable = false)
  var bestiaId: Long = 0,

  /** Its lair, the seal it broke, in metres. It rises here again after a defeat. */
  @Column(name = "lair_x", nullable = false)
  var lairX: Double = 0.0,
  @Column(name = "lair_y", nullable = false)
  var lairY: Double = 0.0,

  /** Where it last stood, in metres, so it walks on from there after a restart. */
  @Column(name = "x", nullable = false)
  var x: Double = 0.0,
  @Column(name = "y", nullable = false)
  var y: Double = 0.0,

  /** One more than the towns it razed: the level of its `DESTROYER` status. */
  @Column(name = "stack", nullable = false)
  var stack: Int = 1,

  /** The settlement it marches on, or null while it has none. */
  @Column(name = "target_settlement")
  var targetSettlement: Int? = null,

  @Column(name = "razed", nullable = false)
  var razed: Int = 0,

  /**
   * The settlements it razed, comma-separated. Kept besides their fall, because a hamlet without buildings never
   * counts as fallen and would draw it back.
   */
  @Column(name = "razed_towns", nullable = false, length = 2048)
  var razedTowns: String = "",

  /** While fallen: the real epoch day it rises again. */
  @Column(name = "returns_at_day")
  var returnsAtDay: Double? = null,

  /** The real epoch day it last fell, for its containment. */
  @Column(name = "last_defeat_day")
  var lastDefeatDay: Double? = null,
) {

  val isFallen: Boolean
    get() = returnsAtDay != null

  fun razedTownList(): List<Int> {
    return razedTowns.split(',').mapNotNull { it.toIntOrNull() }
  }

  companion object {
    const val ONLY = 1L
  }
}

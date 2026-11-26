package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.ForeshadowKind

/**
 * The fate of the current world: which threat, where, when it sets in and how far its clock has come. There is only
 * ever one row. Days are days of world age, scaled for test servers.
 */
@Entity
@Table(name = "doomsday")
class StoredDoomsday(
  @Id
  var id: Long = ONLY,

  /** Null when no threat fits this world: the clock still runs, and the world still ends. */
  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  var kind: DoomKind? = null,

  @Column(name = "origin_x")
  var originX: Double? = null,

  @Column(name = "origin_y")
  var originY: Double? = null,

  @Column(name = "onset_day", nullable = false)
  var onsetDay: Double = 0.0,

  @Column(name = "unfold_days", nullable = false)
  var unfoldDays: Double = 0.0,

  @Column(nullable = false)
  var progress: Double = 0.0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var stage: DoomStage = DoomStage.OMENS,

  /** The world age the clock last ran to. */
  @Column(name = "stepped_to_day", nullable = false)
  var steppedToDay: Double = 0.0,

  /** A GM's fixed pace, or null to take it from the threat. */
  @Column(name = "pace_override")
  var paceOverride: Double? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "foreshadow_kind", length = 32)
  var foreshadowKind: ForeshadowKind? = null,

  @Column(name = "foreshadow_day", nullable = false)
  var foreshadowDay: Double = 0.0,
) {

  val lifeSpanDays: Double
    get() {
      return onsetDay + unfoldDays
    }

  companion object {
    const val ONLY = 1L
  }
}

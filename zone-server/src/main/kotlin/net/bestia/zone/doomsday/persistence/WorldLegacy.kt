package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.doomsday.DoomEnding
import net.bestia.zone.doomsday.DoomKind
import java.time.Instant

/**
 * How an ended world lived and ended. Never wiped with the world, so the worlds that came before stay known: the
 * companion app shows the last one while the next is born, and a later world can remember them.
 */
@Entity
@Table(name = "world_legacy")
class WorldLegacy(
  @Column(name = "world_name", nullable = false)
  val worldName: String,

  @Column(nullable = false)
  val seed: Long,

  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  val threat: DoomKind?,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  val ending: DoomEnding,

  @Column(name = "days_lived", nullable = false)
  val daysLived: Int,

  @Column(name = "t_percent", nullable = false)
  val tPercent: Int,

  @Column(name = "towns_lost", nullable = false)
  val townsLost: Int,

  @Column(name = "born_at", nullable = false)
  val bornAt: Instant,

  @Column(name = "ended_at", nullable = false)
  val endedAt: Instant,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}

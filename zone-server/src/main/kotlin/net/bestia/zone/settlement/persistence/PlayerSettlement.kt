package net.bestia.zone.settlement.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A town a player founded with the Founder skill. See the design docs' player settlements page.
 *
 * Kept after the sign falls, as [State.FALLEN], because the spot stays locked for a while.
 */
@Entity
@Table(name = "player_settlement")
class PlayerSettlement(
  /** The id of the sign site's `player_structure` row, so the tick needs no insert to learn it. */
  @Id
  val id: Long,

  @Column(nullable = false, unique = true, length = 24)
  val name: String,

  @Column(name = "founder_master_id", nullable = false)
  var founderMasterId: Long,

  @Column(name = "founder_account_id", nullable = false)
  var founderAccountId: Long,

  /** A snapshot, so the sign names its founder without a lookup. */
  @Column(name = "founder_name", nullable = false)
  var founderName: String,

  @Column(name = "sign_x", nullable = false)
  val signX: Long,

  @Column(name = "sign_y", nullable = false)
  val signY: Long,

  @Column(name = "sign_z", nullable = false)
  var signZ: Long,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State,

  /** The `player_structure` row of the sign site, while [state] is [State.SITE]. */
  @Column(name = "site_structure_id")
  var siteStructureId: Long?,

  /** Last time somebody put work into the sign site. A site left alone for a day is given up. */
  @Column(name = "site_worked_at", nullable = false)
  var siteWorkedAt: Instant,

  @Column(name = "sign_hp", nullable = false)
  var signHp: Int,

  @Column(name = "fell_at")
  var fellAt: Instant? = null,
) {

  enum class State {
    /** The sign is still being built; the town does not exist yet. */
    SITE,
    STANDING,

    /** The sign was destroyed; the spot is locked until the lock runs out. */
    FALLEN
  }
}

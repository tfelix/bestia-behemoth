package net.bestia.zone.settlement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.util.AccountId
import java.time.Instant

/** In-memory mirror of one [PlayerSettlement] row, plus the size its buildings give it right now. */
data class Town(
  val id: Long,
  val name: String,
  val founderMasterId: Long,
  val founderAccountId: AccountId,
  val founderName: String,
  val sign: Vec3L,
  val state: PlayerSettlement.State,
  val siteStructureId: Long?,
  val siteWorkedAt: Instant,
  val signHp: Int,
  val fellAt: Instant?,
  val size: TownGrowth.Size = TownGrowth.Size(0.0, TownGrowth.BASE_RADIUS_METRES),
) {

  val isStanding: Boolean
    get() {
      return state == PlayerSettlement.State.STANDING
    }

  /** Whether the founder still holds it, so may not found another. A fallen town is nobody's. */
  val isHeld: Boolean
    get() {
      return state != PlayerSettlement.State.FALLEN
    }
}

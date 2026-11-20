package net.bestia.zone.stronghold

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.settlement.StrongholdRoom
import org.springframework.stereotype.Component

/** A town keeps the same distance from an awake seed that a seed keeps from a town when it wakes. */
@Component
class SeedStrongholdRoom(
  private val seeds: EmpyriteSeedRegistry,
  private val config: StrongholdConfig,
) : StrongholdRoom {

  override fun isTooCloseToSeed(at: Vec3L): Boolean {
    return seeds.isAwakeSeedWithin(at, config.settlementClearanceMetres)
  }
}

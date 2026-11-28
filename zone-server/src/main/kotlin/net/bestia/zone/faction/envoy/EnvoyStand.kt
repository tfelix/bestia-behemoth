package net.bestia.zone.faction.envoy

import net.bestia.worldgen.core.Faction
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.geometry.Vec3L

/** Where one Order's Envoy stands, and what the town there is called. */
data class EnvoyStand(
  val faction: Faction,
  val settlementIndex: Int,
  val townName: String,
  val position: Vec3L,
) {

  val target: WorldTarget
    get() {
      return WorldTarget.envoy(faction.name, settlementIndex)
    }
}

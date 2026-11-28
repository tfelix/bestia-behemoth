package net.bestia.zone.faction.envoy

import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.entity.observe.WorldTarget

/** Marks an Order's Envoy standing in settlement [settlementIndex]. */
data class FactionEnvoy(val faction: Faction, val settlementIndex: Int) : Component {

  val target: WorldTarget
    get() {
      return WorldTarget.envoy(faction.name, settlementIndex)
    }
}

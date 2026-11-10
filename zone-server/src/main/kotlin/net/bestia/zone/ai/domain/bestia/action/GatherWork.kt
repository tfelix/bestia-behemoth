package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.effect.Effect
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.Gatherable
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.entity.ecs.WorldObjectIdentity

/** What every way of gathering one prop shares. */
object GatherWork {

  val READS: ComponentClassSet = setOf(WorldObjectIdentity::class)

  fun effectsOf(prop: Gatherable): List<Effect> {
    return listOf(
      Effects.set(BestiaDomain.GATHERED, true),
      Effects.modify(BestiaDomain.GATHERABLES) { seen -> seen.orEmpty() - prop },
      // Working is roaming enough: without this, every pause between two props would be spent wandering off.
      Effects.set(BestiaDomain.RESTLESSNESS, 0),
    )
  }

  /**
   * Whether [prop]'s entity is still that prop. If somebody took it or its column reloaded, the gatherer forgets
   * it now, rather than planning for it again until it next looks around.
   */
  fun stillStands(context: BtContext, prop: Gatherable): Boolean {
    if (context.world.get(prop.entityId, WorldObjectIdentity::class)?.propId == prop.propId) return true

    context.memory.get(BestiaDomain.GATHERABLES)?.let { seen ->
      context.memory.set(BestiaDomain.GATHERABLES, seen - prop, BestiaDomain.GATHERABLES.retain)
    }
    return false
  }
}

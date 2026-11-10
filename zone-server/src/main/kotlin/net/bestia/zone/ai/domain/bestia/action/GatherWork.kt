package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.selector
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
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

  /** Whether [prop]'s entity is still that prop: nobody took it, and its column did not reload. */
  fun stillStands(context: BtContext, prop: Gatherable): Boolean {
    return context.world.get(prop.entityId, WorldObjectIdentity::class)?.propId == prop.propId
  }

  /**
   * Runs [work] on [prop], and gives up on the prop when it fails: taken, out of reach or warded. Left alone for a
   * while, one prop it cannot take does not hold up the rest of the area.
   */
  fun attempt(prop: Gatherable, work: BtNode): BtNode {
    return selector {
      +work
      run("give up on ${prop.propId}") { context ->
        giveUp(context, prop)
        Status.FAILURE
      }
    }
  }

  private fun giveUp(context: BtContext, prop: Gatherable) {
    val memory = context.memory
    memory.get(BestiaDomain.GATHERABLES)?.let { seen ->
      memory.set(BestiaDomain.GATHERABLES, seen - prop, BestiaDomain.GATHERABLES.retain)
    }
    val givenUp = memory.get(BestiaDomain.GIVEN_UP).orEmpty() + prop.propId
    memory.set(BestiaDomain.GIVEN_UP, givenUp, BestiaDomain.GIVEN_UP.retain)
  }
}

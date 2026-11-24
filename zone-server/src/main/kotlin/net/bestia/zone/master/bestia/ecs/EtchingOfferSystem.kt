package net.bestia.zone.master.bestia.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.master.bestia.EtchingOffers
import net.bestia.zone.prop.TreeRegrowthSystem
import org.springframework.stereotype.Component as SpringComponent

/** Ends an etching offer once its Bestia has left the Carving Table, so coming back later does not renew it. */
@SpringComponent
class EtchingOfferSystem(private val offers: EtchingOffers) : System {

  override val phase = Phase.UPKEEP
  override val after = setOf(TreeRegrowthSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = EtchingOffers.READS
  override val writes: ComponentClassSet = setOf(OfferedForEtching::class)

  override fun update(world: World, deltaTime: Float) {
    val ended = mutableListOf<Long>()
    world.each(OfferedForEtching::class) { bestiaId, _ ->
      if (!offers.atTable(world, bestiaId)) ended += bestiaId
    }

    ended.forEach { world.remove(it, OfferedForEtching::class) }
  }
}

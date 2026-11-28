package net.bestia.zone.faction.ecs

import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Gives every sworn master, and each of its bestias, the [FactionBadge] of its Order, and takes it away once the
 * master is unsworn. A bestia whose master is not in the world carries none.
 */
@SpringComponent
class FactionBadgeSystem : System {
  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(FactionMembership::class, Master::class, OwnedBestia::class)

  override val writes: ComponentClassSet = setOf(FactionBadge::class)

  override fun update(world: World, deltaTime: Float) {
    val factionsByMaster = HashMap<Long, Faction>()
    world.query(FactionMembership::class, Master::class).each {
      get<FactionMembership>().faction?.let { factionsByMaster[get<Master>().masterId] = it }
    }

    val wanted = HashMap<EntityId, FactionBadge?>()
    world.query(Master::class).each { id -> wanted[id] = factionsByMaster[get<Master>().masterId]?.let(::FactionBadge) }
    world.query(OwnedBestia::class).each { id ->
      wanted[id] = factionsByMaster[get<OwnedBestia>().masterId]?.let(::FactionBadge)
    }

    wanted.forEach { (id, badge) -> apply(world, id, badge) }
  }

  private fun apply(world: World, id: EntityId, wanted: FactionBadge?) {
    if (world.get(id, FactionBadge::class) == wanted) {
      return
    }

    if (wanted == null) {
      world.remove(id, FactionBadge::class)
    } else {
      world.add(id, wanted)
    }
  }
}

package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.prop.WorldObjectResidencySystem
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.AnchorResidencySystem
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.System as EcsSystem

/**
 * Keeps the generated props under every gatherer's area resident, so it finds sticks and trees with its master
 * offline. Only the area, not the anchor's whole view: those props are far too many to load around every bestia.
 */
@Component
class GatherAreaResidencySystem(
  private val residency: WorldObjectResidencyService,
) : EcsSystem {
  override val phase = Phase.WORLD

  // Before the drain, so a column held here loads in the same tick. After the anchors, which also queue work there.
  override val after = setOf(AnchorResidencySystem::class)
  override val before = setOf(WorldObjectResidencySystem::class)

  // An area only changes with new orders or a moved place, so a second's delay in loading or releasing it costs
  // nothing.
  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, AiAgent::class)

  private val held = HashMap<EntityId, WorkArea>()

  override fun update(world: World, deltaTime: Float) {
    val working = HashSet<EntityId>()

    world.query(OwnedBestia::class, AiAgent::class).each { id ->
      val area = get<AiAgent>().memory.get(BestiaDomain.GATHER_AREA) ?: return@each
      working.add(id)
      follow(id, area)
    }

    // Despawned, or given another job.
    val iterator = held.entries.iterator()
    while (iterator.hasNext()) {
      val (id, area) = iterator.next()
      if (id in working) continue

      residency.releaseArea(area.center, area.radius)
      iterator.remove()
    }
  }

  private fun follow(id: EntityId, area: WorkArea) {
    val previous = held.put(id, area)
    if (previous == area) return

    // The new area first, so the columns both areas share are never released and loaded again.
    residency.holdArea(area.center, area.radius)
    previous?.let { residency.releaseArea(it.center, it.radius) }
  }
}

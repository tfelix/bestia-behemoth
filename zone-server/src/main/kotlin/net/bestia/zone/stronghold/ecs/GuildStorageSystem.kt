package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.SlotStorage
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.GuildStorages
import org.springframework.stereotype.Component as SpringComponent

/** Frees a guild storage once its member walked away from the steward, so the next member may open it. */
@SpringComponent
class GuildStorageSystem(
  private val storages: GuildStorages,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val phase = Phase.UPKEEP

  override val reads: ComponentClassSet = setOf(Master::class, Position::class)
  override val writes: ComponentClassSet = setOf(SlotStorage::class)

  override fun update(world: World, deltaTime: Float) {
    storages.closeLeft(world)
  }
}

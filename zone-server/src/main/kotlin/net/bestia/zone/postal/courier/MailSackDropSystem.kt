package net.bestia.zone.postal.courier

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.honor.PlayerKillHonorSystem
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.spawn.ecs.BestiaBagDropSystem
import net.bestia.zone.spawn.ecs.DeathSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * A courier who is killed drops its mail sacks, while the rest of a master's bag stays with it. The seal sends a
 * sack on unless somebody picks it up in time. The courier is paid nothing for it, but is not struck off.
 */
@SpringComponent
class MailSackDropSystem(
  private val itemTemplates: ItemTemplateRegistry,
  private val couriers: CourierService,
  private val jobs: AsyncJobExecutor,
) : System {

  override val phase = Phase.DEATH
  override val after = setOf(DeathSystem::class, BestiaBagDropSystem::class, PlayerKillHonorSystem::class)
  override val reads: ComponentClassSet = setOf(Dead::class, Master::class, Position::class)
  override val writes: ComponentClassSet = setOf(Inventory::class)

  override fun update(world: World, deltaTime: Float) {
    val sackItemId = itemTemplates.idOf(CourierService.MAIL_SACK) ?: return

    world.query(Dead::class, Master::class, Inventory::class).each { id ->
      val bag = get<Inventory>()
      val sacks = bag.getItems().filter { it.itemId == sackItemId }.map { it.uniqueId }
      if (sacks.isEmpty()) return@each
      val fellAt = world.get(id, Position::class)?.toVec3L() ?: return@each

      // Out of the live bag at once, so the next tick does not drop them again.
      sacks.forEach(bag::removeByUniqueId)
      val courierMasterId = get<Master>().masterId
      jobs.submit(courierMasterId) { couriers.dropSacks(courierMasterId, sacks, fellAt) }
    }
  }
}

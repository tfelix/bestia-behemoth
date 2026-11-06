package net.bestia.zone.postal.courier

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundPickupRule
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Picking up a fallen mail sack takes over its delivery, under the route board's rules: a master who is not struck
 * off and can carry it. The sack stays sealed, so this is not stealing.
 */
@Component
class MailSackPickup(
  private val itemTemplates: ItemTemplateRegistry,
  private val couriers: CourierService,
  private val jobs: AsyncJobExecutor,
) : GroundPickupRule {

  override val reads: ComponentClassSet = setOf(GroundItemStack::class, Master::class, StatusEffects::class)
  override val writes: ComponentClassSet = emptySet()

  override fun mayPickUp(world: World, looter: EntityId, stack: EntityId): Boolean {
    if (sackOf(world, stack) == null) return true
    if (world.get(looter, Master::class) == null) return false

    return world.get(looter, StatusEffects::class)?.hasEffect(StatusEffectId.STRUCK_OFF.id) != true
  }

  override fun onPickUp(world: World, looter: EntityId, stack: EntityId) {
    val sackId = sackOf(world, stack) ?: return
    val courierMasterId = world.get(looter, Master::class)?.masterId ?: return

    jobs.submit(courierMasterId) { couriers.takeOver(sackId, courierMasterId) }
  }

  private fun sackOf(world: World, stack: EntityId): Long? {
    val ground = world.get(stack, GroundItemStack::class) ?: return null

    return ground.uniqueId.takeIf { ground.itemId == itemTemplates.idOf(CourierService.MAIL_SACK) }
  }
}

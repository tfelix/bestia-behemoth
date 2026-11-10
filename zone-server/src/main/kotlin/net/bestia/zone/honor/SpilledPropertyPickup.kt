package net.bestia.zone.honor

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.GroundPickupRule
import net.bestia.zone.item.ecs.SpilledProperty
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Taking spilled goods costs honor for every item, whoever spilled them, unless they were the taker's own. */
@Component
class SpilledPropertyPickup(
  private val honorEvents: HonorEvents,
  private val masters: MasterEntityResolver,
) : GroundPickupRule {

  override val reads: ComponentClassSet =
    setOf(SpilledProperty::class, Account::class, Position::class) + HonorService.READS
  override val writes: ComponentClassSet = HonorEvents.WRITES

  override fun mayPickUp(world: World, looter: EntityId, stack: EntityId): Boolean {
    return true
  }

  /** A bestia that takes them steals for its master. */
  override fun onPickUp(world: World, looter: EntityId, stack: EntityId) {
    val spilled = world.get(stack, SpilledProperty::class) ?: return
    val account = world.get(looter, Account::class)?.accountId ?: return
    if (account == spilled.ownerAccountId) return
    val thief = masters.selectedMasterEntityOf(account) ?: return
    val at = world.get(stack, Position::class)?.toVec3L() ?: return

    honorEvents.stole(world, thief, StolenTier.of(spilled.itemLevel), at)
  }
}

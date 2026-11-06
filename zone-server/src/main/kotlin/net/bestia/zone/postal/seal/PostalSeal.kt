package net.bestia.zone.postal.seal

import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundStackRemoval
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentScript
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.courier.CourierService
import net.bestia.zone.util.EntityId
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** `item_enchantments.yml` id 1: the post's promise on a mail sack. See the seal on the postal page. */
@Component
class PostalSeal(
  private val groundStackRemoval: GroundStackRemoval,
  private val jobs: AsyncJobExecutor,
  // Lazy: the loot spawner runs these scripts, and the courier service spawns loot.
  @Lazy private val couriers: CourierService,
  private val clock: Clock = Clock.systemUTC(),
) : ItemEnchantmentScript {

  /**
   * A fallen sack goes back to the post after [RETURNS_AFTER]. The time lives on the ground copy of the seal, so it
   * survives a restart, and [SealReturnSystem] acts on it. The seal on the sack's row keeps no end.
   */
  override fun onDropped(world: World, groundEntity: EntityId, enchantment: ActiveItemEnchantment) {
    val now = clock.instant()
    val returning = enchantment.copy(endsAt = now.plus(RETURNS_AFTER))
    val ground = world.get(groundEntity, ItemEnchantments::class) ?: return

    world.add(groundEntity, ItemEnchantments(ground.without(enchantment.enchantment).enchantments + returning, now))
  }

  /** The sack opens where it lies; [CourierService.spill] scatters its goods. */
  override fun onDispelled(world: World, groundEntity: EntityId, enchantment: ActiveItemEnchantment) {
    val sackId = world.get(groundEntity, GroundItemStack::class)?.uniqueId ?: return
    val at = world.get(groundEntity, Position::class)?.toVec3L() ?: return

    groundStackRemoval.remove(world, groundEntity)
    jobs.submit(sackId) { couriers.spill(sackId, at) }
  }

  companion object {
    /** Only a Sage with Dispel Lv. 5 has a real chance against it. */
    const val STRENGTH = 70

    val RETURNS_AFTER: Duration = Duration.ofSeconds(30)
  }
}

package net.bestia.zone.item.enchant

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.Random

/**
 * Dispel aimed at an enchanted item on the ground. Each enchantment holds against the cast's Potency with its own
 * strength, and the target's SMDEF plays no part: an item has none. Dispel on a creature is
 * [net.bestia.zone.battle.StatusEffectService.dispel].
 */
@Component
class GroundItemDispel(
  private val catalog: ItemEnchantmentCatalog,
  private val scripts: ItemEnchantmentScripts,
  private val instances: ItemInstanceRepository,
  private val asyncJobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val random: Random = ThreadLocalRandomSource,
) {

  /** On the tick. Returns what broke; each one rolls [Potency.dispelChancePercent] against its strength. */
  fun dispel(world: World, groundEntity: EntityId, baseChancePercent: Int, potency: Int): List<ActiveItemEnchantment> {
    val ground = world.get(groundEntity, ItemEnchantments::class) ?: return emptyList()

    val broken = ground.enchantments.filter { breaks(chancePercentAgainst(it, baseChancePercent, potency)) }
    if (broken.isEmpty()) {
      return broken
    }

    val left = ground.enchantments - broken.toSet()
    if (left.isEmpty()) {
      world.remove(groundEntity, ItemEnchantments::class)
    } else {
      world.add(groundEntity, ground.copy(enchantments = left))
    }
    world.get(groundEntity, GroundItemStack::class)?.uniqueId?.let { forgetOnInstance(it, broken) }
    broken.forEach { scripts.of(it.enchantment).onDispelled(world, groundEntity, it) }

    return broken
  }

  private fun chancePercentAgainst(enchantment: ActiveItemEnchantment, baseChancePercent: Int, potency: Int): Double {
    val definition = catalog.getOrThrow(enchantment.enchantment)
    if (!definition.dispellable) {
      return 0.0
    }

    return Potency.dispelChancePercent(baseChancePercent, potency, enchantment.strength, definition.dispelThreshold)
  }

  private fun breaks(chancePercent: Double): Boolean {
    return random.nextDouble() * 100 < chancePercent
  }

  /** The rows go too, or the item would be enchanted again once it is picked up. */
  private fun forgetOnInstance(instanceId: Long, broken: List<ActiveItemEnchantment>) {
    if (instanceId == 0L) {
      return
    }

    asyncJobs.submit(key = instanceId) {
      transactions.executeWithoutResult {
        val instance = instances.findByIdOrNull(instanceId) ?: return@executeWithoutResult
        broken.forEach { instance.disenchant(it.enchantment) }
      }
    }
  }
}

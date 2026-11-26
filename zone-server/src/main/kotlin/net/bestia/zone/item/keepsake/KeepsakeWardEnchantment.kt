package net.bestia.zone.item.keepsake

import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.item.enchant.ItemEnchantmentScript
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * `item_enchantments.yml` id 2: the ward on the bag a warded bestia dropped. Its [ActiveItemEnchantment.value] is the
 * master it belongs to. A broken ward leaves a plain bundle that anybody may loot.
 */
@Component
class KeepsakeWardEnchantment(
  private val clock: Clock = Clock.systemUTC(),
) : ItemEnchantmentScript {

  /** The time lives on the ground copy, so it survives a restart, and `KeepsakeReturnSystem` acts on it. */
  override fun onDropped(world: World, groundEntity: EntityId, enchantment: ActiveItemEnchantment) {
    val now = clock.instant()
    val ground = world.get(groundEntity, ItemEnchantments::class) ?: return
    val sending = enchantment.copy(endsAt = now.plus(SENDS_HOME_AFTER))

    world.add(groundEntity, ItemEnchantments(ground.without(enchantment.enchantment).enchantments + sending, now))
  }

  companion object {
    /** A fixed Potency above the seal's. Its threshold in `item_enchantments.yml` keeps weak casters off it. */
    const val STRENGTH = 400

    val SENDS_HOME_AFTER: Duration = Duration.ofSeconds(60)

    fun forMaster(masterId: Long): ActiveItemEnchantment {
      return ActiveItemEnchantment(ItemEnchantmentId.KEEPSAKE_WARD, STRENGTH, value = Math.toIntExact(masterId))
    }

    fun masterOf(world: World, groundEntity: EntityId): Long? {
      val ward = world.get(groundEntity, ItemEnchantments::class)?.find(ItemEnchantmentId.KEEPSAKE_WARD) ?: return null

      return ward.value.toLong()
    }
  }
}

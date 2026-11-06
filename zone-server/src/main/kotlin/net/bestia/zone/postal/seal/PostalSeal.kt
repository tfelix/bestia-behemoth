package net.bestia.zone.postal.seal

import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentScript
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** `item_enchantments.yml` id 1: the post's promise on a mail sack. See the seal on the postal page. */
@Component
class PostalSeal(
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

  companion object {
    /** Only a Sage with Dispel Lv. 5 has a real chance against it. */
    const val STRENGTH = 70

    val RETURNS_AFTER: Duration = Duration.ofSeconds(30)
  }
}

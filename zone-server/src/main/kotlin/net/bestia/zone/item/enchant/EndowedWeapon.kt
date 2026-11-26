package net.bestia.zone.item.enchant

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusValueRecalcContext
import org.springframework.stereotype.Component

/**
 * `item_enchantments.yml` id 3: the Sage's Endow, bound to the weapon rather than the wielder. While the weapon is in
 * hand it strikes as the element in [ActiveItemEnchantment.value], with `+5%/lv` attack, as
 * [net.bestia.zone.battle.status.scripts.MasterEndowed] does for a creature without one.
 */
@Component
class EndowedWeapon : ItemEnchantmentScript {

  override fun onWielderRecalc(context: StatusValueRecalcContext, enchantment: ActiveItemEnchantment) {
    context.weaponElement = Element.entries[enchantment.value]
    context.addPercent(attack = ATTACK_PERCENT_PER_LEVEL * enchantment.level)
  }

  companion object {
    const val SECONDS_PER_LEVEL = 60L
    private const val ATTACK_PERCENT_PER_LEVEL = 5
  }
}

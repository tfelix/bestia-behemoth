package net.bestia.zone.postal.seal

import net.bestia.zone.item.enchant.ItemEnchantmentScript
import org.springframework.stereotype.Component

/** `item_enchantments.yml` id 1: the post's promise on a mail sack. See the seal on the postal page. */
@Component
class PostalSeal : ItemEnchantmentScript {

  companion object {
    /** Only a Sage with Dispel Lv. 5 has a real chance against it. */
    const val STRENGTH = 70
  }
}

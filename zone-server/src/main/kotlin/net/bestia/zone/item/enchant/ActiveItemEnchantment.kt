package net.bestia.zone.item.enchant

import java.time.Instant

/** An enchantment one item carries, read off its row or off a ground item. */
data class ActiveItemEnchantment(
  val enchantment: ItemEnchantmentId,
  val strength: Int,
  /** What the instance carries beyond its kind, like an endow's element. */
  val value: Int = 0,
  /** Null for an enchantment that only ends when it is broken or removed. */
  val endsAt: Instant? = null,
) {

  fun hasEnded(now: Instant): Boolean {
    return endsAt != null && !now.isBefore(endsAt)
  }
}

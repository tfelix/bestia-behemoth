package net.bestia.zone.item.enchant

import net.bestia.bnet.proto.HeldItemEnchantmentProto
import java.time.Duration
import java.time.Instant

/** An enchantment on a held item as its holder's client sees it: which one, and how long it has left. */
data class HeldItemEnchantment(
  val enchantmentId: Long,
  /** 0 for an enchantment without an end. */
  val remainingSeconds: Float,
) {

  fun toProto(): HeldItemEnchantmentProto.HeldItemEnchantment {
    return HeldItemEnchantmentProto.HeldItemEnchantment.newBuilder()
      .setEnchantmentId(enchantmentId.toInt())
      .setRemainingSeconds(remainingSeconds)
      .build()
  }

  companion object {
    /** Nothing ticks a held item, so an ended enchantment can still be in memory. It is left out here. */
    fun of(enchantments: List<ActiveItemEnchantment>, now: Instant): List<HeldItemEnchantment> {
      return enchantments
        .filterNot { it.hasEnded(now) }
        .map { HeldItemEnchantment(it.enchantment.id, remainingSecondsOf(it, now)) }
    }

    private fun remainingSecondsOf(enchantment: ActiveItemEnchantment, now: Instant): Float {
      val endsAt = enchantment.endsAt ?: return 0f

      return Duration.between(now, endsAt).toMillis() / 1000f
    }
  }
}
